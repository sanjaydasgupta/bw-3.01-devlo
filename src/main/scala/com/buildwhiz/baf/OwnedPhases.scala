package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

class OwnedPhases extends HttpServlet with HttpUtils {

  private def phase2actions(phase: DynDoc): Seq[DynDoc] = {
    val activityOids = phase.activity_ids[Many[ObjectId]]
    val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityOids)))
    activities.flatMap(_.actions[Many[Document]])
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val projectOid = new ObjectId(parameters("project_id"))
      val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val projectIsPublic = (project has "public") && project.public[Boolean]
      val phaseOids = project.phase_ids[Many[ObjectId]]
      val allPhases: Seq[DynDoc] = BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> phaseOids)))
      val phases = if (projectIsPublic || project.admin_person_id[ObjectId] == personOid) allPhases else
        allPhases.filter(phase => phase.admin_person_id[ObjectId] == personOid ||
          phase2actions(phase).exists(_.assignee_person_id[ObjectId] == personOid))
      writer.print(phases.map(phase => OwnedPhases.processPhase(phase, personOid)).map(phase => bson2json(phase.asDoc))
        .mkString("[", ", ", "]"))
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

object OwnedPhases {

  def processPhase(phase: DynDoc, personOid: ObjectId): DynDoc = {
    val activities: Seq[DynDoc] = BWMongoDB3.activities.
      find(Map("_id" -> Map("$in" -> phase.activity_ids[Many[ObjectId]])))
    val isRelevant = activities.flatMap(_.actions[Many[Document]]).
      exists(_.assignee_person_id[ObjectId] == personOid)
    phase.is_managed = phase.admin_person_id[ObjectId] == personOid
    phase.is_relevant = isRelevant || phase.is_managed[Boolean]
    val actions: Seq[DynDoc] = activities.flatMap(_.actions[Many[Document]])
    if (actions.exists(action => action.status[String] == "waiting" && action.assignee_person_id[ObjectId] == personOid))
      phase.display_status = "waiting"
    else if (actions.exists(action => action.status[String] == "waiting"))
      phase.display_status = "waiting2"
    else
      phase.display_status = phase.status[String]
    val subBpmns: Seq[DynDoc] = phase.bpmn_timestamps[Many[Document]].filter(_.parent_name[String] != "")
    phase.sub_bpmns = subBpmns.sortBy(_.name[String]).map(_.asDoc)
    phase.remove("activity_ids")
    phase
  }

}