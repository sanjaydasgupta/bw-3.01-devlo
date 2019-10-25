package com.buildwhiz.infra

import java.lang.management.ManagementFactory
import java.util.{Calendar, TimeZone, Timer, TimerTask}

import com.buildwhiz.baf2.{ActivityApi, PersonApi, ProcessApi, ProjectApi, SlackApi}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object TimerModule extends HttpUtils {

  val timerTickInMilliseconds: Long = 1 * 60 * 1000L // 1 minute

  private def issueTaskStatusUpdateReminders(ms: Long, project: DynDoc): Unit = {
    val activeProcesses = ProjectApi.allProcesses(project).filter(ProcessApi.isActive)
    val runningActivities = activeProcesses.flatMap(ProcessApi.allActivities).filter(_.status[String] == "running")
    for (activity <- runningActivities) {
      val assignments = ActivityApi.teamAssignment.list(activity._id[ObjectId])
      val mainAssignment = assignments.find(a => a.role[String] == activity.role[String] &&
          a.status[String].matches("running|active"))
      mainAssignment match {
        case Some(assignment) => // Send status update request by Slack/mail
          val personOid = assignment.person_id[ObjectId]
          SlackApi.sendToUser(s"Please file weekly status updates for '${activity.name[String]}'!", Right(personOid))
        case _ =>
      }
    }
  }

  private def fridayMorning(ms: Long, project: DynDoc): Unit = {
    BWLogger.log(classOf[TimerTask].getSimpleName, "fridayMorning", s"Friday for project ${project.name[String]}")
    // Perform any project-specific end-of-week activities here
    issueTaskStatusUpdateReminders(ms, project)
  }

  private def issueTaskDurationReminders(ms: Long, project: DynDoc): Unit = {
    val activeProcesses = ProjectApi.allProcesses(project).filter(ProcessApi.isActive)
    val runningActivities = activeProcesses.flatMap(ProcessApi.allActivities).filter(_.status[String] == "running")
    for (activity <- runningActivities) {
      val assignments = ActivityApi.teamAssignment.list(activity._id[ObjectId])
      val mainAssignment = assignments.find(a => a.role[String] == activity.role[String] &&
        a.status[String].matches("running|active"))
      mainAssignment match {
        case Some(assignment) => // Send duration reminder by Slack/mail
          val personOid = assignment.person_id[ObjectId]
          SlackApi.sendToUser(s"This is a daily reminder for '${activity.name[String]}'!", Right(personOid))
        case _ =>
      }
    }
  }

  private def newDay(ms: Long, project: DynDoc, calendar: Calendar): Unit = {
    BWLogger.log(classOf[TimerTask].getSimpleName, "newDay",
        s"Midnight for project '${project.name[String]}' (${project.tz[String]})")
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    if (dayOfWeek == Calendar.FRIDAY)
      fridayMorning(ms, project)
    // Perform any project-specific daily activities here
    issueTaskDurationReminders(ms, project)
  }

  private def activityDelayedCheck(ms: Long, project: DynDoc, calendar: Calendar): Unit = {
    val activeProcesses = ProjectApi.allProcesses(project).filter(ProcessApi.isActive)
    val runningActivities = activeProcesses.flatMap(ProcessApi.allActivities).filter(_.status[String] == "running")
    for (activity <- runningActivities if !ActivityApi.isDelayed(activity)) {
      val scheduledEndDatetimeMs = ActivityApi.scheduledEnd(activity) match {
        case Some(ms) => ms
        case None => 0
      }
      if (ms > scheduledEndDatetimeMs) {
        BWLogger.log(classOf[TimerTask].getSimpleName, "activityDelayedCheck",
          s"ERROR: Activity is delayed: ${activity.name[String]} (${activity._id[ObjectId]})")
        ActivityApi.setDelayed(activity, delayed=true)
        val stillActiveAssignees = ActivityApi.teamAssignment.list(activity._id[ObjectId]).
            filter(_.status[String] == "running").map(_.person_id[ObjectId])
        for (recipient <- ActivityApi.managers(activity) ++ stillActiveAssignees) {
          SlackApi.sendToUser(s"Activity is delayed: ${activity.name[String]}", Right(recipient))
        }
      }
    }
  }

    private def processHealthCheck(ms: Long): Unit = {
    val allProcesses = ProcessApi.listProcesses()
    val goodProcesses = allProcesses.filterNot(ProcessApi.isZombie)
    val newZombies = goodProcesses.filterNot(ProcessApi.isHealthy)
    for (p <- newZombies) {
      BWMongoDB3.processes.updateOne(Map("_id" -> p._id[ObjectId]), Map($set -> Map("isZombie" -> true)))
      val processIdentity = s"${p.name[String]} (${p._id[ObjectId]})"
      for (admin <- PersonApi.listAdmins) {
        SlackApi.sendToUser(s"Process apparently killed: $processIdentity", Left(admin))
      }
      BWLogger.log(classOf[TimerTask].getSimpleName, "processHealthCheck",
          s"ERROR: Process apparently killed: $processIdentity")
    }
  }

  private def fifteenMinutes(ms: Long): Unit = {
    BWLogger.log(classOf[TimerTask].getSimpleName, "fifteenMinutes", "15-Minute-Tick", performanceData(): _*)
    val projects = ProjectApi.listProjects()
    for (project <- projects) {
      val calendar = Calendar.getInstance(TimeZone.getTimeZone(project.tz[String]))
      calendar.setTimeInMillis(ms)
      activityDelayedCheck(ms, project, calendar)
      val hours = calendar.get(Calendar.HOUR_OF_DAY)
      if (hours == 0) {
        val minutes = calendar.get(Calendar.MINUTE)
        if (minutes == 0)
          newDay(ms, project, calendar)
      }
    }
    // Perform any global quarter-hourly activities here
    processHealthCheck(ms)
  }

  private def performanceData(): Seq[(String, String)] = {
    val runtime = sys.runtime
    val (freeMemory, maxMemory) = (runtime.freeMemory(), runtime.maxMemory())
    val sysLoadAvg = ManagementFactory.getOperatingSystemMXBean.getSystemLoadAverage
    val threadCount = Thread.activeCount()
    Seq("Free-Memory" -> freeMemory, "Max-Memory" -> maxMemory, "Thread-Count" -> threadCount,
      "Sys-Load-Avg" -> sysLoadAvg).map(kv => (kv._1, kv._2.toString))
  }

  private def timerTicks(): Unit = {
    try {
      val ms: Long = System.currentTimeMillis()
      val millisecondsIn15Minutes = 15 * 60 * 1000
      val msModulo15Minutes = (ms % millisecondsIn15Minutes).asInstanceOf[Int]
      if (msModulo15Minutes > millisecondsIn15Minutes - 5000 || msModulo15Minutes < 20000)
        Future {fifteenMinutes(ms)}
      // perform scheduled tasks
    } catch {
      case t: Throwable =>
        BWLogger.log(classOf[TimerTask].getSimpleName, "timerTicks",
          s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
    }
  }

  private val bwTimer = new Timer(true)

  private object timerTask extends TimerTask {
    override def run(): Unit = {
      timerTicks()
    }
  }

  def scheduleTimer(): Unit = {
    timerTicks()
    val millisNow = System.currentTimeMillis
    val millisTillNextTimerTick = timerTickInMilliseconds - (millisNow % timerTickInMilliseconds)
    bwTimer.scheduleAtFixedRate(timerTask, millisTillNextTimerTick, timerTickInMilliseconds)
  }

  def cancelTimer(): Unit = bwTimer.cancel()
}
