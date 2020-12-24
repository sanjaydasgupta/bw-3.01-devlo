package com.buildwhiz.baf3

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId
import com.buildwhiz.baf2.{ActivityApi, PersonApi, PhaseApi, ProcessApi}

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.collection.JavaConverters._

class PhaseInfo extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
      val phaseRecord: DynDoc = PhaseApi.phaseById(phaseOid)
      val user: DynDoc = getUser(request)
      response.getWriter.print(PhaseInfo.phase2json(phaseRecord, user))
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

object PhaseInfo extends DateTimeUtils {

  private def isEditable(phase: DynDoc, user: DynDoc): Boolean = {
    val userOid = user._id[ObjectId]
    phase.admin_person_id[ObjectId] == userOid || PhaseApi.canManage(userOid, phase)
  }

  private def deliverables(task: DynDoc, user: DynDoc): Many[Document] = {
    val deliverables: Seq[DynDoc] = if (task.has("deliverables")) task.deliverables[Many[Document]] else Nil
    val deliverableRecords: Seq[Document] = deliverables.map(deliverable => {
      val rawEndDate = deliverable.bpmn_scheduled_end_date[Long]
      val endDate = if (rawEndDate == -1) {
        "Not available"
      } else {
        val timeZone = user.tz[String]
        dateTimeString(deliverable.bpmn_scheduled_end_date[Long], Some(timeZone))
      }
      val status = deliverable.status[String]
      val scope = status match {
        case "defined" => "Future"
        case "running" => "Current"
        case "ended" => "Past"
        case _ => "?"
      }
      Map("_id" -> deliverable._id[ObjectId].toString, "name" -> deliverable.name[String],
          "bpmn_name" -> deliverable.bpmn_name[String], "status" -> status, "display_status" -> "Dormant",
          "due_date" -> endDate, "scope" -> scope)
    })
    deliverableRecords.asJava
  }

  private def taskInformation(phase: DynDoc, user: DynDoc): Many[Document] = {
    val firstProcess: Option[DynDoc] = PhaseApi.allProcesses(phase._id[ObjectId]).headOption
    val tasks = firstProcess.toSeq.flatMap(p => ProcessApi.allActivities(p))
    val taskRecords: Seq[Document] = tasks.map(task => {
      val rawEndDate = task.bpmn_scheduled_end_date[Long]
      val endDate = if (rawEndDate == -1) {
        "Not available"
      } else {
        val timeZone = user.tz[String]
        dateTimeString(task.bpmn_scheduled_end_date[Long], Some(timeZone))
      }
      val status = task.status[String]
      val scope = status match {
        case "defined" => "Future"
        case "running" => "Current"
        case "ended" => "Past"
        case _ => "?"
      }
      Map("_id" -> task._id[ObjectId].toString, "name" -> task.name[String], "bpmn_name" -> task.bpmn_name[String],
          "status" -> status, "display_status" -> ActivityApi.displayStatus2(task), "due_date" -> endDate,
          "scope" -> scope, "deliverables" -> deliverables(task, user))
    })
    taskRecords.asJava
  }

  def phase2json(phase: DynDoc, user: DynDoc): String = {
    val editable = isEditable(phase, user)
    val description = new Document("editable", editable).append("value", phase.description[String])
    val status = new Document("editable", false).append("value", phase.status[String])
    val displayStatus = new Document("editable", false).append("value", PhaseApi.displayStatus(phase))
    val name = new Document("editable", editable).append("value", phase.name[String])
    val rawPhaseManagers = phase.assigned_roles[Many[Document]].filter(_.role_name[String] == "Project-Manager").
      map(role => {
        val thePerson = PersonApi.personById(role.person_id[ObjectId])
        val personName = PersonApi.fullName(thePerson)
        new Document("_id", thePerson._id[ObjectId].toString).append("name", personName)
      }).asJava
    val phaseManagers = new Document("editable", editable).append("value", rawPhaseManagers)
    //val canManage = PhaseApi.canManage(user._id[ObjectId], phase)
    val projectDoc = new Document("name", name).append("description", description).append("status", status).
      append("display_status", displayStatus).append("managers", phaseManagers).
      append("task_info", taskInformation(phase, user))
    projectDoc.toJson
  }

}