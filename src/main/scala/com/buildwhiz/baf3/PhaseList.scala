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
      val isAdmin = PersonApi.isBuildWhizAdmin(Right(user))
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
    val (bpmnName, displayStatus) = PhaseApi.allProcesses(phase._id[ObjectId]).headOption match {
      case Some(theProcess) => (theProcess.bpmn_name[String], PhaseApi.displayStatus(phase))
      case None => ("NA", "error")
    }
    val phaseOid = phase._id[ObjectId]
    val (dateStart, dateEnd) = if (phaseOid.hashCode() % 2 == 0) {
      ("2020-06-01", "2020-07-31")
    } else {
      ("2020-06-01", "2021-05-31")
    }
    val projectDocument = new Document("name", phase.name[String]).append("_id", phaseOid.toString).
      append("managers", managerNames).append("display_status", displayStatus).
      append("date_start", dateStart).append("date_end", dateEnd).
      append("budget", "0.00").append("expenditure", "0.00").append("bpmn_name", bpmnName)
    bson2json(projectDocument)
  }

}