package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class TaskStatusInfo extends HttpServlet with HttpUtils with DateTimeUtils {

  private def taskStatusRecord(user: DynDoc, theActivity: DynDoc, request: HttpServletRequest): String = {
    def wrap(rawValue: Any, editable: Boolean): Document = {
      new Document("editable", editable).append("value", rawValue)
    }

    val reportingInterval = if (theActivity.has("reporting_interval"))
      theActivity.reporting_interval[String]
    else
      "weekly"

    val changeLog = ActivityApi.changeLogItems(user, theActivity)

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
    val percentComplete = changeLog.find(_.has("percent_complete")) match {
      case None => "0"
      case Some(d) => d.get("percent_complete", classOf[Any]).toString
    }

    val userOid = user._id[ObjectId]
    val parentProcess = ActivityApi.parentProcess(theActivity._id[ObjectId])
    val userCanManage = ProcessApi.canManage(userOid, parentProcess)

    val allAssignments = ActivityApi.teamAssignment.list(theActivity._id[ObjectId])
    val activeAssignments = allAssignments.filter(a => a.status[String].matches("active|started") &&
      (userCanManage || a.person_id[ObjectId] == userOid))
    val completedAssignmentsExists = allAssignments.exists(!_.status[String].matches("defined|active|started"))

    val userCanContribute = activeAssignments.nonEmpty
    val userCanReview = userCanManage && completedAssignmentsExists
    //val untilStart = theActivity.status[String].matches("defined")
    val untilEnd = theActivity.status[String].matches("defined|running")

    //val enableEditUntilStart = userCanManage && untilStart
    val enableEditUntilEnd = userCanManage && untilEnd
    val enableEditButton = userCanManage

    val enableUpdateStatusButton = userCanContribute || userCanReview

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

    val endDates = Seq("end_date_pessimistic", "end_date_likely", "end_date_optimistic").map(dateType => {
      val date: String = if (theActivity.has(dateType))
        dateTimeString(theActivity.asDoc.getLong(dateType), Some(user.tz[String]))
      else
        "NA"
      (dateType, date)
    })

    val actualDuration = ActivityApi.actualDuration(theActivity) match {
      case -1 => "NA"
      case d: Float => d.toString
    }

    val estimatedDuration = ActivityApi.scheduledDuration(theActivity) match {
      case -1 => "NA"
      case d: Float => d.toString
    }

    val isAdmin = PersonApi.isBuildWhizAdmin(Right(user))
    val record = new Document("status", wrap(ActivityApi.stateSubState(theActivity), editable = false)).
      append("on_critical_path", wrap(theActivity.on_critical_path[String], editable = false)).
      append("estimated_duration", wrap(estimatedDuration, editable = false)).
      append("actual_duration", wrap(actualDuration, editable = false)).
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
      append("end_date_not_before", earliestStartDate).
      append("end_date_pessimistic", wrap(scheduledEnd, editable = false)).
      append("end_date_likely", wrap(scheduledEnd, editable = false)).
      append("end_date_optimistic", wrap(scheduledEnd, editable = false))
    for (ed <- endDates) {
      val (dateType, date) = ed
      record.append(dateType, wrap(date, editable = false))
    }
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