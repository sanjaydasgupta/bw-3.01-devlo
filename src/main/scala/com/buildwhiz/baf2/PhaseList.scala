package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class PhaseList extends HttpServlet with HttpUtils {
  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val user: DynDoc = getUser(request)
      val personOid = user._id[ObjectId]
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
      val isAdmin = PersonApi.isBuildWhizAdmin(Right(freshUserRecord))
      val parentProject: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val phases: Seq[DynDoc] = if (isAdmin) {
        ProjectApi.allPhases(parentProject)
      } else {
        ProjectApi.phasesByUser(personOid, parentProject)
      }
      response.getWriter.print(phases.map(phase2json(_, parentProject, personOid)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${phases.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  def phase2json(phase: DynDoc, project: DynDoc, personOid: ObjectId): String = {
    val processes: Seq[DynDoc] = PhaseApi.allProcesses(phase)
    val activities: Seq[DynDoc] = processes.flatMap(process => ProcessApi.allActivities(Right(process)))
    val actions = Seq.empty[DynDoc] //: Seq[DynDoc] = activities.flatMap(_.actions[Many[Document]])
    val isActive = PhaseApi.isActive(phase)
    val isManaged = phase.admin_person_id[ObjectId] == personOid
    val legacyStatus: String = if (actions.exists(action => action.status[String] == "waiting" &&
        action.assignee_person_id[ObjectId] == personOid))
      "waiting"
    else if (actions.exists(action => action.status[String] == "waiting"))
      "waiting2"
    else
      phase.status[String]
    val projectDocument = new Document("name", phase.name[String]).append("_id", phase._id[ObjectId].toString).
      append("status", phase.status[String]).append("legacy_status", legacyStatus).
      append("display_status", PhaseApi.displayStatus(phase)).
      append("is_managed", isManaged).append("admin_person_id", phase.admin_person_id[ObjectId].toString).
      append("can_delete", !isActive).append("docsUrl", s"docs?phase_id=${phase._id[ObjectId]}")
    bson2json(projectDocument)
  }

}