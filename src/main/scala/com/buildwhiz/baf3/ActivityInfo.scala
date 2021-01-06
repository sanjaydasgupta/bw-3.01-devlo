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

  private def activity2json(activity: DynDoc, user: DynDoc): String = {
    val editable = isEditable(activity, user)
    val (dur_opti_raw, dur_pessi_raw, dur_likely_raw, dur_actual_raw) = activity.get[Document]("durations") match {
      case None => ("NA", "NA", "NA", "NA")
      case Some(durations) => (
          durations.getOrDefault("optimistic", "NA"), durations.getOrDefault("pessimistic", "NA"),
          durations.getOrDefault("likely", "NA"), durations.getOrDefault("actual", "NA"))
    }
    def resolve(opts: Option[String]): String = opts match {case None => "NA"; case Some(s) => s}
    val durationOptimistic = new Document("editable", editable).append("value", dur_opti_raw)
    val durationPessimistic = new Document("editable", editable).append("value", dur_pessi_raw)
    val durationLikely = new Document("editable", editable).append("value", dur_likely_raw)
    val durationActual = new Document("editable", false).append("value", dur_actual_raw)
    val latestStart = new Document("editable", editable).
        append("value", resolve(activity.get[String]("latest_start")))
    val dateStart = new Document("editable", editable).
        append("value", resolve(activity.get[String]("date_start")))
    val dateEnd = new Document("editable", editable).
        append("value", resolve(activity.get[String]("date_end")))
    val description = new Document("editable", editable).append("value", activity.description[String])
    val status = new Document("editable", false).append("value", activity.status[String])
    val displayStatus = new Document("editable", false).append("value", ActivityApi.displayStatus2(activity))
    val name = new Document("editable", editable).append("value", activity.name[String])
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