package com.buildwhiz.infra

import java.lang.management.ManagementFactory
import java.util.{Calendar, TimeZone, Timer, TimerTask}
import java.io.File
import com.buildwhiz.baf2.{ActivityApi, PersonApi, PhaseApi, ProcessApi, ProjectApi}
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils3}
import org.bson.types.ObjectId
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.slack.SlackApi
import org.bson.Document

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys.process._

object TimerModule extends HttpUtils with MailUtils3 {

  private lazy val adminRecord: Option[DynDoc] =
    BWMongoDB3.persons.find(Map("first_name" -> "Sanjay", "last_name" -> "Admin")).headOption

  private val timerTickInMilliseconds: Long = 1 * 60 * 1000L // 1 minute

  private def issueTaskStatusUpdateReminders(ms: Long, project: DynDoc): Unit = {
    try {
      val activeProcesses = ProjectApi.allProcesses(project).filter(ProcessApi.isActive)
      val runningActivities = activeProcesses.flatMap(pr => ProcessApi.allActivities(Right(pr))).filter(_.status[String] == "running")
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
        BWLogger.log(getClass.getName, "LOCAL",
          s"ERROR (issueTaskStatusUpdateReminders): ${t.getClass.getSimpleName}(${t.getMessage})")
    }
  }

  private def fridayMorning(ms: Long, project: DynDoc): Unit = {
    try {
      BWLogger.log(getClass.getName, "LOCAL", s"fridayMorning (Project '${project.name[String]}')")
      // Perform any project-specific end-of-week activities here
      issueTaskStatusUpdateReminders(ms, project)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "LOCAL",
          s"ERROR (fridayMorning): ${t.getClass.getSimpleName}(${t.getMessage})")
    }
  }

  private def issueTaskDurationReminders(ms: Long, project: DynDoc): Unit = {
    try {
      val activeProcesses = ProjectApi.allProcesses(project).filter(ProcessApi.isActive)
      val runningActivities = activeProcesses.flatMap(pr => ProcessApi.allActivities(Right(pr))).filter(_.status[String] == "running")
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
        BWLogger.log(getClass.getName, "LOCAL",
          s"ERROR (issueTaskDurationReminders): ${t.getClass.getSimpleName}(${t.getMessage})")
    }
  }

  private def newDay(ms: Long, project: DynDoc, calendar: Calendar): Unit = {
    try {
      BWLogger.log(getClass.getName, "LOCAL",
        s"newDay (Project: '${project.name[String]}' TZ: '${ProjectApi.timeZone(project)}')")
      val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
      if (dayOfWeek == Calendar.FRIDAY)
        fridayMorning(ms, project)
      // Perform any project-specific daily activities here
      //issueTaskDurationReminders(ms, project)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "LOCAL",
          s"ERROR (newDay): ${t.getClass.getSimpleName}(${t.getMessage})")
    }
  }

  private def activityDelayedCheck(ms: Long, project: DynDoc, calendar: Calendar): Unit = {
    try {
      //BWLogger.log(getClass.getName, "activityDelayedCheck", s"project: ${project.name[String]}")
      val activeProcesses = ProjectApi.allProcesses(project).filter(ProcessApi.isActive)
      val runningActivities = activeProcesses.flatMap(pr => ProcessApi.allActivities(Right(pr))).filter(_.status[String] == "running")
      for (activity <- runningActivities if !ActivityApi.isDelayed(activity)) {
        val scheduledEndDatetimeMs = ActivityApi.scheduledEnd(activity) match {
          case Some(ms) => ms
          case None => 0
        }
        val delayed: Boolean = ms > scheduledEndDatetimeMs
        BWLogger.log(getClass.getName, "LOCAL",
            "activityDelayedCheck (Project: '%s', Activity: '%s', Delayed: %b)".
            format(project.name[String], activity.name[String], delayed))
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
        BWLogger.log(getClass.getName, "LOCAL",
          s"ERROR (activityDelayedCheck): ${t.getClass.getSimpleName}(${t.getMessage})")
    }
  }

  private def reportToAdmins(): Unit = {
    val t0 = System.currentTimeMillis() - 86400000L
    val usersAggregator: Many[Document] = Seq(
      new Document("$match",
        new Document("milliseconds", new Document($gte, t0)).append("variables.u$nm", new Document($exists, true))),
      new Document("$group", new Document("_id", "$variables.u$nm").append("count", new Document("$sum", 1))),
      new Document("$sort", new Document("count", -1))
    )
    val users: Seq[DynDoc] = BWMongoDB3.trace_log.aggregate(usersAggregator)
    val message = if (users.nonEmpty) {
      s"""The following users were active in the past 24 hours:\n${users.map(_._id[String]).mkString(", ")}"""
    } else {
      "No users were active in the past 24 hours"
    }
    val instanceName = BWMongoDB3.instance_info.find().headOption match {
      case Some(ii) => ii.instance[String]
      case None => "Unknown"
    }
    val fns = Seq("Sanjay", "Prabhas")
    val admins: Seq[DynDoc] = BWMongoDB3.persons.find(Map("last_name" -> "Admin", "first_name" -> Map($in -> fns)))
    sendMail(admins.map(_._id[ObjectId]), s"Report from '$instanceName'", message, None)
  }

  private def trimTraceLogCollection(ms: Long): Unit = {
    try {
      val millisecondsOneDayAgo = ms - 86400000L
      val millisecondsThreeMonthsAgo = ms - 86400000L * 92
      val recordsToDeleteQuery = Map(
        $or -> Seq(
          Map("milliseconds" -> Map($lt -> millisecondsThreeMonthsAgo)),
          Map(
            $and -> Seq(
              Map("milliseconds" -> Map($lt -> millisecondsOneDayAgo)),
              Map("event_name" -> Map($not -> Map($regex -> "^(?:AUDIT|ENTRY|ERROR|EXIT).+")))
            )
          )
        )
      )
      val deleteResult = BWMongoDB3.trace_log.deleteMany(recordsToDeleteQuery)
      val deletedCount = deleteResult.getDeletedCount
      reportToAdmins()
      BWLogger.log(getClass.getName, "LOCAL", s"trimTraceLogCollection ($deletedCount records deleted)")
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "LOCAL",
          s"ERROR (trimTraceLogCollection): ${t.getClass.getSimpleName}(${t.getMessage})")
    }
  }

  private def notifyLogEvents(ms: Long): Unit = {
    try {
      val startTimestamp = ms - 15 * 60 * 1000
      val matcher: DynDoc = Map("$match" -> Map($and -> Seq(
        Map("milliseconds" -> Map($gte -> startTimestamp)),
        Map("event_name" -> Map($regex -> "(?i)Error"))
      )))
      val grouper: DynDoc = Map("$group" -> Map(
        "_id" -> "$variables.u$nm",
        "process_ids" -> Map($push -> "$process_id")
      ))
      val errors: Seq[DynDoc] = BWMongoDB3.trace_log.aggregate(Seq(matcher.asDoc, grouper.asDoc))
      def nameFromId(id: String): String = {
        if (id == null) {
          id
        } else {
          val endOffset = id.indexOf("(")
          if (endOffset == -1) {
            id
          } else {
            id.substring(0, endOffset)
          }
        }
      }
      if (errors.nonEmpty) {
        val errorString = "Events: " + errors.map(e => s"${nameFromId(e._id[String])}->" +
          s"""${e.process_ids[Many[String]].map(_.replaceAll("baf[23./]*", "")).distinct.mkString("[", ", ", "]")}""").
          mkString("\n")
        adminRecord match {
          case Some(ar) =>
            SlackApi.sendNotification(errorString, Left(ar))
          case None =>
            BWLogger.log(getClass.getName, "LOCAL", "ERROR (notifyLogEvents): user 'Sanjay Admin' not found")
        }
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "LOCAL", s"ERROR (notifyLogEvents): ${t.getClass.getSimpleName}(${t.getMessage})")
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
        BWMongoDB3.tasks.updateOne(Map("_id" -> Map($in -> allActivityOids)), Map($set -> Map("is_zombie" -> true)))
        val phase = ProcessApi.parentPhase(zp._id[ObjectId])
        val project = PhaseApi.parentProject(phase._id[ObjectId])
        val processIdentity = s"${project.name[String]}/${phase.name[String]}/${zp.name[String]}"
        for (admin <- PersonApi.listAdmins) {
          SlackApi.sendToUser(Left(s"Process killed: $processIdentity"), Left(admin))
        }
        BWLogger.log(getClass.getName, "LOCAL", s"INFO: processHealthCheck (Process killed: $processIdentity)")
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "LOCAL",
          s"ERROR (processHealthCheck): ${t.getClass.getSimpleName}(${t.getMessage})")
    }
  }

  private def saveDatabases(calendar: Calendar): Unit = {
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
          BWLogger.log(getClass.getName, "LOCAL", "saveDatabases (Delete outdated %s, status: %s)".
              format(archive.getName, deleteStatus))
        }
      }
    }
    val logMessage = if (status == 0)
      s"saveDatabases (Created archive: $dbArchiveName)"
    else
      s"saveDatabases (ERROR failed to create archive $dbArchiveName, status: $status)"
    BWLogger.log(getClass.getName, "LOCAL", logMessage)
  }

  private def fifteenMinutes(ms: Long): Unit = {
    try {
      val projects = ProjectApi.listProjects()
      //val message = projects.map(_.name[String]).mkString("15-Minute-Tick projects: ", ", ", "")
      //BWLogger.log(getClass.getName, "fifteenMinutes", message, performanceData(): _*)
      for (project <- projects) {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(ProjectApi.timeZone(project)))
        calendar.setTimeInMillis(ms)
        //activityDelayedCheck(ms, project, calendar)
        val hours = calendar.get(Calendar.HOUR_OF_DAY)
        if (hours == 0) {
          val minutes = calendar.get(Calendar.MINUTE)
          if (minutes == 0)
            newDay(ms, project, calendar)
        }
      }
      // Database archival etc (at 3 AM PST)
      val calendarPST = Calendar.getInstance(TimeZone.getTimeZone("PST"))
      calendarPST.setTimeInMillis(ms)
      val hours = calendarPST.get(Calendar.HOUR_OF_DAY)
      if (hours == 3) {
        val minutes = calendarPST.get(Calendar.MINUTE)
        if (minutes == 0) {
          // at midnight of PST timezone
          Future {
            saveDatabases(calendarPST)
            trimTraceLogCollection(ms)
          }
        }
      }
      // Perform any global quarter-hourly activities here
      processHealthCheck(ms)
      //notifyLogEvents(ms)
      ProcessApi.checkProcessSchedules(ms)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "LOCAL",
          s"ERROR (fifteenMinutes): ${t.getClass.getSimpleName}(${t.getMessage})")
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
        BWLogger.log(getClass.getName, "LOCAL",
          s"ERROR (timerTicks): ${t.getClass.getSimpleName}(${t.getMessage})")
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
