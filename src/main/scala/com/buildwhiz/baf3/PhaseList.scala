package com.buildwhiz.baf3

import com.buildwhiz.baf2.{PersonApi, PhaseApi, ProjectApi}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId
import scala.jdk.CollectionConverters._

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class PhaseList extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val t0 = System.currentTimeMillis()
    val parameters = getParameterMap(request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val user: DynDoc = getPersona(request)
      val personOid = user._id[ObjectId]
      val isAdmin = PersonApi.isBuildWhizAdmin(Right(user))
      val parentProject: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val phases: Seq[DynDoc] = if (isAdmin) {
        ProjectApi.allPhases(parentProject)
      } else {
        val allPhases = ProjectApi.phasesByUser30(personOid, parentProject)
        allPhases.filter(p => PhaseApi.allProcesses(p).nonEmpty)
      }
      val phaseInfoList: Many[Document] = phases.map(phase2json).map(_.asDoc).asJava
      val hostName = getHostName(request)
      val menuItems = displayedMenuItems(isAdmin, request, ProjectApi.canManage(personOid, parentProject))
      val result = new Document("menu_items", menuItems).append("phases", phaseInfoList)
      response.getWriter.print(result.toJson)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      val delay = System.currentTimeMillis() - t0
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${phases.length}, time: $delay ms)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  def phase2json(phase: DynDoc): DynDoc = {
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
    Map("name" -> phase.name[String], "_id"-> phaseOid.toString, "managers"-> managerNames,
      "display_status"-> displayStatus, "date_start"-> dateStart, "date_end"-> dateEnd,
      "budget"-> "0.00", "expenditure"-> "0.00", "bpmn_name"-> bpmnName)
  }

}