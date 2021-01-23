package com.buildwhiz.baf3

import com.buildwhiz.baf2.{ActivityApi, PersonApi}
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class ActivityInfo extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val activityOid = new ObjectId(parameters("activity_id"))
      val activityRecord: DynDoc = ActivityApi.activityById(activityOid)
      val user: DynDoc = getUser(request)
      response.getWriter.print(ActivityInfo.activity2json(activityRecord, user))
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

object ActivityInfo extends DateTimeUtils {

  private def isEditable(activity: DynDoc, user: DynDoc): Boolean = {
    val userOid = user._id[ObjectId]
    PersonApi.isBuildWhizAdmin(Right(user)) || ActivityApi.canManage(userOid, activity)
  }

  private def wrap(value: String, canEdit: Boolean) = new Document("editable", canEdit).append("value", value)

  private def durations(activity: DynDoc, user: DynDoc): (Document, Document, Document, Document) = {
    val editable = isEditable(activity, user) && activity.status[String] != "ended"
    val durations = activity.get[Document]("durations") match {
      case None => ("NA", "NA", "NA", "NA")
      case Some(durations) => (
          durations.getOrDefault("optimistic", "NA").toString, durations.getOrDefault("pessimistic", "NA").toString,
          durations.getOrDefault("likely", "NA").toString, durations.getOrDefault("actual", "NA").toString
      )
    }
    (wrap(durations._1, editable), wrap(durations._2, editable), wrap(durations._3, editable),
        wrap(durations._4, canEdit = false))
  }

  private def activity2json(activity: DynDoc, user: DynDoc): String = {
    val editable = isEditable(activity, user)
    def resolve(opts: Option[String]): String = opts match {case None => "NA"; case Some(s) => s}
    val (durationOptimistic, durationPessimistic, durationLikely, durationActual) = durations(activity, user)
    val latestStart = wrap(resolve(activity.get[String]("latest_start")), canEdit = false)
    val dateStart = wrap(resolve(activity.get[String]("date_start")), canEdit = false)
    val dateEnd = wrap(resolve(activity.get[String]("date_end")), canEdit = false)
    val description = wrap(activity.description[String], editable)
    val status = wrap(activity.status[String], canEdit = false)
    val displayStatus = wrap(ActivityApi.displayStatus2(activity), canEdit = false)
    val name = wrap(activity.name[String], canEdit = false)
//    val bpmnName: String = PhaseApi.allProcesses(activity._id[ObjectId]).headOption match {
//      case Some(theProcess) => theProcess.bpmn_name[String]
//      case None => "not-available"
//    }
    val phaseDoc = new Document("name", name).append("description", description).append("status", status).
        append("display_status", displayStatus).//append("bpmn_name", bpmnName).
        append("duration_optimistic", durationOptimistic).append("duration_pessimistic", durationPessimistic).
        append("duration_likely", durationLikely).append("duration_actual", durationActual).
        append("latest_start", latestStart).append("date_start", dateStart).append("date_end", dateEnd)
    phaseDoc.toJson
  }

}