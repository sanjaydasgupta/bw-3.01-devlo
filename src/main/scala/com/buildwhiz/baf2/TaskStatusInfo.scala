package com.buildwhiz.baf2

import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class TaskStatusInfo extends HttpServlet with HttpUtils with DateTimeUtils {

  private def changeLogItems(user: DynDoc, theActivity: DynDoc): Seq[Document] = {
    val changeLogEntries: Seq[DynDoc] = if (theActivity.has("change_log"))
      theActivity.change_log[Many[Document]]
    else
      Seq.empty[DynDoc]
    changeLogEntries.map(entry => {
      val dateTime = dateTimeString(entry.timestamp[Long], Some(user.tz[String]))
      val updatedBy = if (entry.has("updater_person_id")) {
        val updaterOid = entry.updater_person_id[ObjectId]
        val updater = PersonApi.personById(updaterOid)
        s"${updater.first_name} ${updater.last_name}"
      } else
        "-"
      val percentComplete = if (entry.has("percent_complete")) entry.percent_complete[Any].toString else "-"
      new Document("date_time", dateTime).append("updated_by", updatedBy).append("percent_complete", percentComplete).
        append("description", entry.description[String])
    })
  }

  private def taskStatusRecord(user: DynDoc, theActivity: DynDoc, request: HttpServletRequest): String = {
    def wrap(rawValue: Any, editable: Boolean): Document = {
      new Document("editable", editable).append("value", rawValue)
    }

    val accessLevel: String = ActivityApi.userAccessLevel(user, theActivity)

    val reportingInterval = if (theActivity.has("reporting_interval"))
      theActivity.reporting_interval[String]
    else
      "weekly"

    val changeLog = changeLogItems(user, theActivity)

    val timezone = user.tz[String]
    val scheduledStart = ActivityApi.scheduledStart(theActivity) match {
      case None => "NA"
      case Some(ms) => dateTimeString(ms, Some(timezone)).split(" ").head
    }
    val actualStart = ActivityApi.actualStart(theActivity) match {
      case None => "NA"
      case Some(ms) => dateTimeString(ms, Some(timezone)).split(" ").head
    }
    val scheduledEnd = ActivityApi.scheduledEnd(theActivity) match {
      case None => "NA"
      case Some(ms) => dateTimeString(ms, Some(timezone)).split(" ").head
    }
    val actualEnd = ActivityApi.actualEnd(theActivity) match {
      case None => "NA"
      case Some(ms) => dateTimeString(ms, Some(timezone)).split(" ").head
    }
    val percentComplete = changeLog.reverse.find(_.has("percent_complete")) match {
      case None => "0"
      case Some(d) => d.get("percent_complete", classOf[Any]).toString
    }

    val userCanManage = accessLevel.matches("all|manage")
    val userCanContribute = accessLevel.matches("contribute")

    val untilStart = theActivity.status[String].matches("defined")
    val untilEnd = theActivity.status[String].matches("defined|running")

    val enableEditUntilStart = userCanManage && untilStart
    val enableEditUntilEnd = userCanManage && untilEnd
    val enableEditButton = userCanManage && untilEnd

    val enableUpdateStatusButton = theActivity.status[String] == "running" && (userCanManage || userCanContribute)

    val activeRoles = ActivityApi.teamAssignment.list(theActivity._id[ObjectId]).
      find(a => a.status[String] == "started").map(_.role[String])

    val updateReportOptions = activeRoles match {
      case Some("Pre-Approval") => Seq("Pre-Approval-OK, Pre-Approval-Comment")
      case Some("Post-Approval") => Seq("Post-Approval-OK, Post-Approval-Comment")
      case Some(r) if !r.matches("CC|Others") => Seq("Complete, In-Progress")
      case _ => Seq.empty[String]
    }

    val record = new Document("status", wrap(ActivityApi.stateSubState(theActivity), editable = false)).
        append("on_critical_path", wrap(theActivity.on_critical_path[String], editable = false)).
        append("estimated_duration", wrap(ActivityApi.scheduledDuration(theActivity), editable = false)).
        append("actual_duration", wrap(ActivityApi.actualDuration(theActivity), editable = false)).
        append("estimated_start_date", wrap(scheduledStart, enableEditUntilStart)).
        append("actual_start_date", wrap(actualStart, editable = false)).
        append("estimated_end_date", wrap(scheduledEnd, enableEditUntilEnd)).
        append("actual_end_date", wrap(actualEnd, editable = false)).
        append("reporting_interval", wrap(reportingInterval, enableEditUntilEnd)).
        append("percent_complete", wrap(percentComplete, editable = false)).append("change_log", changeLog).
        append("enable_edit_button", enableEditButton).
        append("enable_update_status_button", enableUpdateStatusButton).
        append("update_report_options", updateReportOptions)
    bson2json(record)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val activityOid = new ObjectId(parameters("activity_id"))
      val theActivity = ActivityApi.activityById(activityOid)
      val user: DynDoc = getUser(request)
      val freshUserRecord = PersonApi.personById(user._id[ObjectId])
      response.getWriter.print(taskStatusRecord(freshUserRecord, theActivity, request))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}