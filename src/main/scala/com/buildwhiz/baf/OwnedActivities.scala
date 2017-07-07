package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class OwnedActivities extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val phaseOid = new ObjectId(parameters("phase_id"))
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val activityOids = phase.activity_ids[Many[ObjectId]]
      val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityOids)))
      val bpmnName = parameters("bpmn_name")
      writer.print(activities.map(a => OwnedActivities.processActivity(a, personOid)).
        filter(_.bpmn_name[String] == bpmnName).map(activity => bson2json(activity.asDoc)).
        mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}

object OwnedActivities {

  def processActivity(activity: DynDoc, personOid: ObjectId): DynDoc = {
    //activity.display_status = activity.status[String]
    val actions = activity.actions[Many[Document]]
    if (actions.exists(action => action.status[String] == "waiting" && action.assignee_person_id[ObjectId] == personOid))
      activity.display_status = "waiting"
    else if (actions.exists(action => action.status[String] == "waiting"))
      activity.display_status = "waiting2"
    else if (actions.exists(action => action.status[String] == "defined"))
      activity.display_status = "defined"
    else if (actions.exists(action => action.status[String] == "ended"))
      activity.display_status = "ended"
    activity.is_relevant = actions.exists(_.assignee_person_id[ObjectId] == personOid)
    activity.asDoc.remove("actions")
    activity
  }
}