package com.buildwhiz.infra

import java.lang.management.ManagementFactory
import java.util.{Calendar, TimeZone, Timer, TimerTask}

import com.buildwhiz.baf2.{ActivityApi, ProcessApi, ProjectApi, SlackApi}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object TimerModule extends HttpUtils {

  val timerTickInMilliseconds: Long = 1 * 60 * 1000L // 1 minute

  private def fridayMorning(ms: Long, project: DynDoc): Unit = {
    BWLogger.log(classOf[TimerTask].getSimpleName, "fridayMorning", s"Friday for project ${project.name[String]}")
    // Perform any project-specific end-of-week activities here
    val activeProcesses = ProjectApi.allProcesses(project).filter(ProcessApi.isActive)
    val runningActivities = activeProcesses.flatMap(ProcessApi.allActivities).filter(_.status[String] == "running")
    for (activity <- runningActivities) {
      val assignments = ActivityApi.teamAssignment.list(activity._id[ObjectId])
      val mainAssignment = assignments.find(a => a.role[String] == activity.role[String] &&
          a.status[String].matches("running|active"))
      mainAssignment match {
        case Some(assignment) => // Send status update request by Slack/mail
          val personOid = assignment.person_id[ObjectId]
          SlackApi.sendToUser(s"Time to file status report for '${activity.name[String]}'!", Right(personOid))
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
  }

  private def fifteenMinutes(ms: Long): Unit = {
    BWLogger.log(classOf[TimerTask].getSimpleName, "fifteenMinutes", "15-Minute-Tick", performanceData(): _*)
    val projects = ProjectApi.listProjects()
    for (project <- projects) {
      val calendar = Calendar.getInstance(TimeZone.getTimeZone(project.tz[String]))
      calendar.setTimeInMillis(ms)
      val hours = calendar.get(Calendar.HOUR_OF_DAY)
      if (hours == 0) {
        val minutes = calendar.get(Calendar.MINUTE)
        if (minutes == 0)
          newDay(ms, project, calendar)
      }
    }
    // Perform any global quarter-hourly activities here
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
    val ms: Long = System.currentTimeMillis()
    val millisecondsIn15Minutes = 15 * 60 * 1000
    val msModulo15Minutes = (ms % millisecondsIn15Minutes).asInstanceOf[Int]
    if (msModulo15Minutes > millisecondsIn15Minutes - 5000 || msModulo15Minutes < 20000)
      Future {fifteenMinutes(ms)}
    // perform scheduled tasks
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
