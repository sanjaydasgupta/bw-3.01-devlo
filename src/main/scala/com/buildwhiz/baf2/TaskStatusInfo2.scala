package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

/*
1) The Update Status button will be enabled whenever a PM is logged in,
and views the Task Details page of a completed task.
This feature does not require any change in the UI code as the enable/disable status of the button
is indicated by the server -- by setting a true/false value in the
enable_update_status_button element of the result of the baf2/TaskStatusInfo API.

2) The text displayed on the face of the current Update Status button will be variable.
The text to be displayed on this button will be indicated by the server in a new element named
update_button_text to be added to the result of the baf2/TaskStatusInfo API.

3) The panel that is popped up when the Update Status button is clicked will have the following changes:
    a) The title text (currently always Update Status) will be variable.
    The text to be displayed as title will be indicated by the server in a new element named
    update_panel_title to be added to the result of the baf2/TaskStatusInfo API.
    b) The % Complete (text and input field) may or may not be displayed.
    An additional boolean indicator display_percent_complete will be added to the result of the
    baf2/TaskStatusInfo API to indicate if the % Complete (text and input field) should be displayed.
    c) The text above the status selector dropdown (currently *Status) will be variable.
    The text to be displayed will be indicated by the server in new element named
    status_dropdown_title to be added to the result of the baf2/TaskStatusInfo API.

4) All other details will remain unchanged. There will be no changes in the baf2/TaskStatusReport API.
*/

class TaskStatusInfo2 extends HttpServlet with HttpUtils with DateTimeUtils {

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

    val userOid = user._id[ObjectId]
    val parentProcess = ActivityApi.parentProcess(theActivity._id[ObjectId])
    val userCanManage = ProcessApi.canManage(userOid, parentProcess)

    val activeAssignments = ActivityApi.teamAssignment.list(theActivity._id[ObjectId]).
        filter(a => a.status[String].matches("active|started") &&
        (userCanManage || a.person_id[ObjectId] == userOid))

    val userCanContribute = activeAssignments.nonEmpty
    //val untilStart = theActivity.status[String].matches("defined")
    val untilEnd = theActivity.status[String].matches("defined|running")

    //val enableEditUntilStart = userCanManage && untilStart
    val enableEditUntilEnd = userCanManage && untilEnd
    val enableEditButton = userCanManage

    val enableUpdateStatusButton = userCanContribute

    val activityUpdateReportOptions = activeAssignments.flatMap(assignment => assignment.role[String] match {
      case RoleListSecondary.preApproval => Seq("Pre-Approval-OK", "Pre-Approval-Comment")
      case RoleListSecondary.postApproval => Seq("Post-Approval-OK", "Post-Approval-Comment")
      // case RoleListSecondary.cc => Seq(???, ???)
      case r if !RoleListSecondary.secondaryRoles.contains(r) => Seq("Complete", "In-Progress")
      case other => Seq("BAD-ROLE", other.toString)
    })

    val (updateButtonText, updatePanelTitle, statusDropdownTitle, updateReportOptions) =
        if (theActivity.status[String] == "ended" && userCanManage)
          ("Update Review", "Review Update", "Rating", (1 to 5).map(_.toString))
        else
          ("Update Status", "Status Update", "Status", activityUpdateReportOptions)

    val earliestStartDate = if (actualStart == "NA") {
      if (scheduledStart == "NA")
        "NA"
      else
        scheduledStart
    } else
      actualStart

    val isAdmin = PersonApi.isBuildWhizAdmin(Right(user))
    val record = new Document("status", wrap(ActivityApi.stateSubState(theActivity), editable = false)).
        append("on_critical_path", wrap(theActivity.on_critical_path[String], editable = false)).
        append("estimated_duration", wrap(ActivityApi.scheduledDuration(theActivity), editable = false)).
        append("actual_duration", wrap(ActivityApi.actualDuration(theActivity), editable = false)).
        append("estimated_start_date", wrap(scheduledStart, isAdmin)).
        append("actual_start_date", wrap(actualStart, isAdmin)).
        append("estimated_end_date", wrap(scheduledEnd, isAdmin)).
        append("actual_end_date", wrap(actualEnd, isAdmin)).
        append("reporting_interval", wrap(reportingInterval, enableEditUntilEnd)).
        append("percent_complete", wrap(percentComplete, editable = false)).append("change_log", changeLog).
        append("enable_edit_button", enableEditButton).
        append("enable_update_status_button", enableUpdateStatusButton).
        append("update_report_options", updateReportOptions).
        append("update_button_text", updateButtonText).
        append("update_panel_title", updatePanelTitle).
        append("display_percent_complete", userCanContribute).
        append("status_dropdown_title", statusDropdownTitle).
        append("end_date_not_before", earliestStartDate)
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