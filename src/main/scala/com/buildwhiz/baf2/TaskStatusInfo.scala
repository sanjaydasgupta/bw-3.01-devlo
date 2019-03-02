package com.buildwhiz.baf2

import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class TaskStatusInfo extends HttpServlet with HttpUtils with DateTimeUtils {

  private def changeLogItems(user: DynDoc, theActivity: DynDoc, theAction: DynDoc): Seq[Document] = {
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
      val percentComplete = if (entry.has("percent_complete")) entry.percent_complete[String] else "-"
      new Document("date_time", dateTime).append("updated_by", updatedBy).append("percent_complete", percentComplete).
        append("description", entry.description[String])
    })
  }

  private def taskStatusRecord(user: DynDoc, theActivity: DynDoc, theAction: DynDoc, request: HttpServletRequest): String = {
    def wrap(rawValue: Any, editable: Boolean): Document = {
      new Document("editable", editable).append("value", rawValue)
    }

    val reportingInterval = if (theActivity.has("reporting_interval"))
      theActivity.reporting_interval[String]
    else
      "weekly"

    val (actualStartDate, actualEndDate) = if (theAction.has("timestamps")) {
      val timestamps: DynDoc = theAction.timestamps[Document]
      if (timestamps.has("end")) {
        (timestamps.start[Long], timestamps.end[Long])
      } else if (timestamps.has("start")) {
        (timestamps.start[Long], "NA")
      } else {
        ("NA", "NA")
      }
    } else {
      ("NA", "NA")
    }

    val changeLog = changeLogItems(user, theActivity, theAction)
    val record = new Document("status", wrap(theAction.status[String], editable = false)).
        append("on_critical_path", wrap(theAction.on_critical_path[String], editable = false)).
        append("estimated_duration", wrap(theAction.duration[String], editable = false)).
        append("actual_duration", wrap(35, editable = false)).
        append("estimated_start_date", wrap("2018-MM-DD", editable = false)).
        append("actual_start_date", wrap(actualStartDate, editable = false)).
        append("estimated_end_date", wrap("2019-MM-DD", editable = false)).
        append("actual_end_date", wrap(actualEndDate, editable = false)).
        append("reporting_interval", wrap(reportingInterval, editable = false)).
        append("change_log", changeLog)
    bson2json(record)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val activityOid = new ObjectId(parameters("activity_id"))
      val theActivity = ActivityApi.activityById(activityOid)
      val actions = ActivityApi.allActions(theActivity)
      val theAction = actions.find(_.`type`[String] == "main") match {
        case Some(a) => a
        case None => throw new IllegalArgumentException(s"Could not find 'main' action")
      }
      val actionName = theAction.name[String]
      val user: DynDoc = getUser(request)
      val freshUserRecord = PersonApi.personById(user._id[ObjectId])
      response.getWriter.print(taskStatusRecord(freshUserRecord, theActivity, theAction, request))
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