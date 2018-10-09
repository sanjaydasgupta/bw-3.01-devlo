package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.engine.{ProcessEngineException, ProcessEngines}

class OwnedPhases extends HttpServlet with HttpUtils with DateTimeUtils {

  private def phase2actions(phase: DynDoc): Seq[DynDoc] = {
    val activityOids = phase.activity_ids[Many[ObjectId]]
    val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityOids)))
    activities.flatMap(_.actions[Many[Document]])
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val personOid = user._id[ObjectId]
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
      val isAdmin = freshUserRecord.roles[Many[String]].contains("BW-Admin")
      val projectOid = new ObjectId(parameters("project_id"))
      val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val projectIsPublic = (project has "public") && project.public[Boolean]
      val phaseOids = project.phase_ids[Many[ObjectId]]
      val allPhases: Seq[DynDoc] = BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> phaseOids)))
      val phases = if (isAdmin || projectIsPublic || project.admin_person_id[ObjectId] == personOid)
        allPhases
      else
        allPhases.filter(phase => phase.admin_person_id[ObjectId] == personOid ||
          phase2actions(phase).exists(_.assignee_person_id[ObjectId] == personOid))
      for (phase <- phases) {
        val managerOid = phase.admin_person_id[ObjectId]
        val manager: DynDoc = BWMongoDB3.persons.find(Map("_id" -> managerOid)).head
        phase.manager = s"${manager.first_name[String]} ${manager.last_name[String]}"
        val timeStamps: DynDoc = phase.timestamps[Document]
        phase.start_date = if (timeStamps.has("start"))
          dateTimeString(timeStamps.start[Long], Some(freshUserRecord.tz[String]))
        else
          "0000-00-00 00:00"
      }
      response.getWriter.print(phases.map(phase => OwnedPhases.processPhase(phase, project, personOid)).map(phase => bson2json(phase.asDoc))
        .mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object OwnedPhases {

  def healthy(phase: DynDoc): Boolean = {
    if (phase.status[String] == "running") {
      val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
      try {
        rts.getVariables(phase.process_instance_id[String])
        true
      } catch {
        case _: ProcessEngineException => false
      }
    } else
      true
  }

  def processPhase(phase: DynDoc, project: DynDoc, personOid: ObjectId): DynDoc = {
    val activities: Seq[DynDoc] = BWMongoDB3.activities.
      find(Map("_id" -> Map("$in" -> phase.activity_ids[Many[ObjectId]])))
    val isRelevant = activities.flatMap(_.actions[Many[Document]]).
      exists(_.assignee_person_id[ObjectId] == personOid)
    phase.can_launch = phase.status[String] == "defined" && project.status[String] == "running"
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
    phase.healthy = healthy(phase)
    phase.remove("activity_ids")
    phase
  }

}