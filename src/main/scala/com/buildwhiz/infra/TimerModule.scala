package com.buildwhiz.infra

import java.lang.management.ManagementFactory
import java.util.{Calendar, TimeZone, Timer, TimerTask}
import java.io.File

import com.buildwhiz.baf2.{ActivityApi, PersonApi, PhaseApi, ProcessApi, ProjectApi}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.slack.SlackApi

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import scala.sys.process._

object TimerModule extends HttpUtils {

  val timerTickInMilliseconds: Long = 1 * 60 * 1000L // 1 minute

  private def issueTaskStatusUpdateReminders(ms: Long, project: DynDoc): Unit = {
    try {
      val activeProcesses = ProjectApi.allProcesses(project).filter(ProcessApi.isActive)
      val runningActivities = activeProcesses.flatMap(ProcessApi.allActivities).filter(_.status[String] == "running")
      for (activity <- runningActivities) {
        val assignments = ActivityApi.teamAssignment.list(activity._id[ObjectId])
        val mainAssignment = assignments.find(a => a.role[String] == activity.role[String] &&
          a.status[String].matches("running|active"))
        mainAssignment match {
          case Some(assignment) => // Send status update request by Slack/mail
            val personOid = assignment.person_id[ObjectId]
            SlackApi.sendToUser(Left(s"Please file weekly status updates for '${activity.name[String]}'!"), Right(personOid))
          case _ =>
        }
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "issueTaskStatusUpdateReminders",
          s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
    }
  }

  private def fridayMorning(ms: Long, project: DynDoc): Unit = {
    try {
      BWLogger.log(getClass.getName, "fridayMorning", s"Friday for project ${project.name[String]}")
      // Perform any project-specific end-of-week activities here
      issueTaskStatusUpdateReminders(ms, project)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "fridayMorning",
          s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
    }
  }

  private def issueTaskDurationReminders(ms: Long, project: DynDoc): Unit = {
    try {
      val activeProcesses = ProjectApi.allProcesses(project).filter(ProcessApi.isActive)
      val runningActivities = activeProcesses.flatMap(ProcessApi.allActivities).filter(_.status[String] == "running")
      for (activity <- runningActivities) {
        val assignments = ActivityApi.teamAssignment.list(activity._id[ObjectId])
        val mainAssignment = assignments.find(a => a.role[String] == activity.role[String] &&
          a.status[String].matches("running|active"))
        mainAssignment match {
          case Some(assignment) => // Send duration reminder by Slack/mail
            val personOid = assignment.person_id[ObjectId]
            SlackApi.sendToUser(Left(s"This is a daily reminder for '${activity.name[String]}'!"), Right(personOid))
          case _ =>
        }
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "issueTaskDurationReminders",
          s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
    }
  }

  private def newDay(ms: Long, project: DynDoc, calendar: Calendar): Unit = {
    try {
      BWLogger.log(getClass.getName, "newDay",
        s"Midnight for project '${project.name[String]}' (${ProjectApi.timeZone(project)})")
      val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
      if (dayOfWeek == Calendar.FRIDAY)
        fridayMorning(ms, project)
      // Perform any project-specific daily activities here
      issueTaskDurationReminders(ms, project)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "newDay",
          s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
    }
  }

  private def activityDelayedCheck(ms: Long, project: DynDoc, calendar: Calendar): Unit = {
    try {
      //BWLogger.log(getClass.getName, "activityDelayedCheck", s"project: ${project.name[String]}")
      val activeProcesses = ProjectApi.allProcesses(project).filter(ProcessApi.isActive)
      val runningActivities = activeProcesses.flatMap(ProcessApi.allActivities).filter(_.status[String] == "running")
      for (activity <- runningActivities if !ActivityApi.isDelayed(activity)) {
        val scheduledEndDatetimeMs = ActivityApi.scheduledEnd(activity) match {
          case Some(ms) => ms
          case None => 0
        }
        val delayed: Boolean = ms > scheduledEndDatetimeMs
        BWLogger.log(getClass.getName, "activityDelayedCheck",
          s"project: ${project.name[String]}, activity: ${activity.name[String]}, delayed: $delayed")
        if (delayed) {
          ActivityApi.setDelayed(activity, delayed = true)
          val stillActiveAssignees = ActivityApi.teamAssignment.list(activity._id[ObjectId]).
            filter(_.status[String] == "running").map(_.person_id[ObjectId])
          for (recipient <- (ActivityApi.managers(activity) ++ stillActiveAssignees).distinct) {
            SlackApi.sendToUser(Left(s"Activity is delayed: ${activity.name[String]}"), Right(recipient))
          }
        }
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "activityDelayedCheck",
          s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
    }
  }

  private def processHealthCheck(ms: Long): Unit = {
    try {
      val allProcesses = ProcessApi.listProcesses()
      val nonZombies = allProcesses.filterNot(ProcessApi.isZombie)
      val newZombies = nonZombies.filterNot(ProcessApi.isHealthy)
      for (zp <- newZombies) {
        BWMongoDB3.processes.updateOne(Map("_id" -> zp._id[ObjectId]), Map($set -> Map("is_zombie" -> true)))
        val allActivityOids: Seq[ObjectId] = zp.activity_ids[Many[ObjectId]]
        BWMongoDB3.activities.updateOne(Map("_id" -> Map($in -> allActivityOids)), Map($set -> Map("is_zombie" -> true)))
        val phase = ProcessApi.parentPhase(zp._id[ObjectId])
        val project = PhaseApi.parentProject(phase._id[ObjectId])
        val processIdentity = s"${project.name[String]}/${phase.name[String]}/${zp.name[String]}"
        for (admin <- PersonApi.listAdmins) {
          SlackApi.sendToUser(Left(s"Process killed: $processIdentity"), Left(admin))
        }
        BWLogger.log(getClass.getName, "processHealthCheck", s"INFO: Process killed: $processIdentity")
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "processHealthCheck",
          s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
    }
  }

  private def archiveDatabases(calendar: Calendar): Unit = {
    BWLogger.log(getClass.getName, "saveDatabases", "ENTRY")
    val dbDirectoryName = s"camunda-h2-dbs"
    calendar.setTimeZone(TimeZone.getTimeZone("GMT"))
    val dbArchiveName = "%d-%02d-%02d-%s.zip".format(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DATE), dbDirectoryName)
    val cmd = Seq("zip", "-r", dbArchiveName, dbDirectoryName)
    val status = Process(cmd).!
    if (status == 0) {
      val NBR_OF_ARCHIVES_TO_KEEP = 3
      val archiveFiles = new File(".").listFiles(_.getName.matches(s"\\d{4}-\\d{2}-\\d{2}-$dbDirectoryName.zip"))
      if (archiveFiles.length > NBR_OF_ARCHIVES_TO_KEEP) {
        val archivesSortedByDate = archiveFiles.sortBy(_.getName)
        val outdatedArchives = archivesSortedByDate.take(archiveFiles.length - NBR_OF_ARCHIVES_TO_KEEP)
        for (archive <- outdatedArchives) {
          val deleteStatus = archive.delete()
          BWLogger.log(getClass.getName, "saveDatabases", s"Delete outdated (${archive.getName}, status: $deleteStatus)")
        }
      }
    }
    val logMessage = if (status == 0)
      s"EXIT-OK (Created archive $dbArchiveName)"
    else
      s"EXIT-ERROR (FAILED to create archive $dbArchiveName, status: $status)"
    BWLogger.log(getClass.getName, "saveDatabases", logMessage)
  }

  private def fifteenMinutes(ms: Long): Unit = {
    try {
      val projects = ProjectApi.listProjects()
      //val message = projects.map(_.name[String]).mkString("15-Minute-Tick projects: ", ", ", "")
      //BWLogger.log(getClass.getName, "fifteenMinutes", message, performanceData(): _*)
      for (project <- projects) {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(ProjectApi.timeZone(project)))
        calendar.setTimeInMillis(ms)
        activityDelayedCheck(ms, project, calendar)
        val hours = calendar.get(Calendar.HOUR_OF_DAY)
        if (hours == 0) {
          val minutes = calendar.get(Calendar.MINUTE)
          if (minutes == 0)
            newDay(ms, project, calendar)
        }
      }
      // Database archival etc (at midnight PST)
      val calendarPST = Calendar.getInstance(TimeZone.getTimeZone("PST"))
      calendarPST.setTimeInMillis(ms)
      val hours = calendarPST.get(Calendar.HOUR_OF_DAY)
      if (hours == 0) {
        val minutes = calendarPST.get(Calendar.MINUTE)
        if (minutes == 0) {
          // at midnight of PST timezone
          Future {archiveDatabases(calendarPST)}
        }
      }
      // Perform any global quarter-hourly activities here
      processHealthCheck(ms)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "fifteenMinutes",
          s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
    }
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
        BWLogger.log(getClass.getName, "timerTicks",
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
