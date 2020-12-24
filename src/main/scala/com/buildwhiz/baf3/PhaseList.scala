package com.buildwhiz.baf3

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import com.buildwhiz.baf2.{PersonApi, PhaseApi, ProjectApi}

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
      response.getWriter.print(phases.map(phase2json).mkString("[", ", ", "]"))
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

  def phase2json(phase: DynDoc): String = {
    val managerOids = PhaseApi.managers(Right(phase))
    val managerNames = PersonApi.personsByIds(managerOids).map(PersonApi.fullName).mkString(", ")
    val projectDocument = new Document("name", phase.name[String]).append("_id", phase._id[ObjectId].toString).
      append("managers", managerNames).append("display_status", PhaseApi.displayStatus(phase)).
      append("date_start", "Not available").append("date_end", "Not available").
      append("budget", "0.00").append("expenditure", "0.00")
    bson2json(projectDocument)
  }

}