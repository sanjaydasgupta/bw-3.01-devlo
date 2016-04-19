package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import BWMongoDB3._
import com.buildwhiz.Utils
import org.bson.types.ObjectId

import scala.collection.JavaConversions._

class OwnedPhases extends HttpServlet with Utils {

  private def phase2actions(phase: DynDoc): Seq[DynDoc] = {
    val activityOids = phase.activity_ids[ObjectIdList]
    val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityOids))).toSeq
    activities.flatMap(_.actions[DocumentList])
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val projectOid = new ObjectId(parameters("project_id"))
      val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val projectIsPublic = (project ? "public") && project.public[Boolean]
      val phaseOids = project.phase_ids[ObjectIdList]
      val allPhases: Seq[DynDoc] = BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> phaseOids))).toSeq
      val phases = if (projectIsPublic) allPhases else
        allPhases.filter(phase => phase2actions(phase).exists(_.assignee_person_id[ObjectId] == personOid))
      writer.print(phases.map(phase => OwnedPhases.processPhase(phase, personOid)).map(phase => bson2json(phase.asDoc))
        .mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}

object OwnedPhases {

  def processPhase(phase: DynDoc, personOid: ObjectId): DynDoc = {
    val activities: Seq[DynDoc] = BWMongoDB3.activities.
      find(Map("_id" -> Map("$in" -> phase.activity_ids[ObjectIdList]))).toSeq
    val isRelevant = activities.flatMap(_.actions[DocumentList]).
      exists(_.assignee_person_id[ObjectId] == personOid)
    phase.is_managed = phase.admin_person_id[ObjectId] == personOid
    phase.is_relevant = isRelevant || phase.is_managed[Boolean]
    val actions: Seq[DynDoc] = activities.flatMap(_.actions[DocumentList])
    if (actions.exists(action => action.status[String] == "waiting" && action.assignee_person_id[ObjectId] == personOid))
      phase.display_status = "waiting"
    else if (actions.exists(action => action.status[String] == "waiting"))
      phase.display_status = "waiting2"
    else
      phase.display_status = phase.status[String]
    phase.asDoc.remove("activity_ids")
    phase
  }

}