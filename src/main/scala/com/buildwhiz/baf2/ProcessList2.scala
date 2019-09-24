package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class ProcessList2 extends HttpServlet with HttpUtils with DateTimeUtils {

  def process2document(process: DynDoc, user: DynDoc): Document = {
    val adminPersonOid = process.admin_person_id[ObjectId]
    val adminPerson: DynDoc = BWMongoDB3.persons.find(Map("_id" -> adminPersonOid)).head
    val adminName = PersonApi.fullName(adminPerson)
    val timestamps: DynDoc = process.timestamps[Document]
    val timeZone = user.tz[String]
    val (startTime, endTime) = if (timestamps.has("end")) {
      (dateTimeString(timestamps.start[Long], Some(timeZone)), dateTimeString(timestamps.end[Long], Some(timeZone)))
    } else if (timestamps.has("start")) {
      (dateTimeString(timestamps.start[Long], Some(timeZone)), "NA")
    } else {
      ("NA", "NA")
    }
    val allActivitiesAssigned = ProcessApi.allActivityOids(process).forall(activityOid => {
      val assignments = ActivityApi.teamAssignment.list(activityOid)
      assignments.forall(_.has("person_id"))
    })
    val parentPhase = ProcessApi.parentPhase(process._id[ObjectId])
    val canLaunch = allActivitiesAssigned && PhaseApi.canManage(user._id[ObjectId], parentPhase) &&
      process.status[String] == "defined"
    new Document("_id", process._id[ObjectId].toString).append("name", process.name[String]).
      append("status", process.status[String]).append("display_status", ProcessApi.displayStatus(process)).
      append("start_time", startTime).append("end_time", endTime).
      append("admin_person_id", adminPersonOid.toString).append("bpmn_name", process.bpmn_name[String]).
      append("manager", adminName).append("can_launch", canLaunch)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val user: DynDoc = getUser(request)
      val Seq(optPhaseId, optProjectId) = Seq("phase_id", "project_id").map(parameters.get)

      val (processes, parentProject, canAddProcess, addButtonToolTipText) = (optPhaseId, optProjectId) match {
        case (Some(phaseId), _) =>
          val phaseOid = new ObjectId(phaseId)
          if (!PhaseApi.exists(phaseOid))
            throw new IllegalArgumentException(s"Bad phase_id: $phaseId")
          val canAddProcess = PhaseApi.canManage(user._id[ObjectId], PhaseApi.phaseById(phaseOid))
          val toolTipText = if (canAddProcess) "" else "No permission"
          (PhaseApi.allProcesses(phaseOid), PhaseApi.parentProject(phaseOid), canAddProcess, toolTipText)
        case (None, Some(projectId)) =>
          val projectOid = new ObjectId(projectId)
          if (!ProjectApi.exists(projectOid))
            throw new IllegalArgumentException(s"Bad project_id: $projectOid")
          val toolTipText = "Can't add process to a project - select a phase within the project"
          (ProjectApi.allProcesses(projectOid), ProjectApi.projectById(projectOid), false, toolTipText)
      }

      val detail = parameters.get("detail") match {
        case None => false
        case Some(dv) => dv.toBoolean
      }

      val processDetails: Many[Document] = if (detail) {
        processes.map(process => ProcessApi.processProcess(process, parentProject, user._id[ObjectId]).asDoc).asJava
      } else {
        processes.map(process => process2document(process, user)).asJava
      }

      val result = new Document("process_list", processDetails).append("can_add_process", canAddProcess).
          append("add_button_tool_tip_text", addButtonToolTipText)

      response.getWriter.print(result.toJson)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${processes.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}