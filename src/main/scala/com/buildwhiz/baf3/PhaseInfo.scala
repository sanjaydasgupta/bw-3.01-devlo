package com.buildwhiz.baf3

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId
import com.buildwhiz.baf2.{ActivityApi, PersonApi, PhaseApi}

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.collection.JavaConverters._

class PhaseInfo extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
      val phaseRecord: DynDoc = PhaseApi.phaseById(phaseOid)
      val user: DynDoc = getPersona(request)
      response.getWriter.print(PhaseInfo.phase2json(phaseRecord, user, request: HttpServletRequest))
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

object PhaseInfo extends HttpUtils with DateTimeUtils {

  private def isEditable(phase: DynDoc, user: DynDoc): Boolean = {
    val userOid = user._id[ObjectId]
    PhaseApi.canManage(userOid, phase)
  }

  private def getDeliverables(phase: DynDoc, user: DynDoc): Seq[DynDoc] = {
    val phaseTeamOids = PhaseApi.allTeamOids30(phase).toSet
    val myTeams = TeamApi.teamsByMemberOid(user._id[ObjectId])
    val myPhaseTeams = myTeams.filter(team => phaseTeamOids.contains(team._id[ObjectId]))
    DeliverableApi.deliverablesByTeamOids(myPhaseTeams.map(_._id[ObjectId]))
  }

  private def deliverableInformation(deliverables: Seq[DynDoc]): Many[Document] = {
    val deliverableRecords: Seq[Document] = deliverables.map(deliverable => {
      val status = deliverable.status[String]
      val scope = status match {
        case "Not-Started" => "Future"
        case "Completed" | "Ended" => "Past"
        case _ => "Current"
      }
      Map("_id" -> deliverable._id[ObjectId].toString, "activity_id" -> deliverable.activity_id[ObjectId].toString,
        "name" -> deliverable.name[String], "status" -> status, "due_date" -> "Not Available", "scope" -> scope)
    })
    deliverableRecords.asJava
  }

  private def taskInformation(deliverables: Seq[DynDoc], user: DynDoc): Many[Document] = {
    val taskStatusValues = DeliverableApi.taskStatusMap(deliverables)
    val activityOids = deliverables.map(_.activity_id[ObjectId])
    val tasks = ActivityApi.activitiesByIds(activityOids)
    val taskRecords: Seq[Document] = tasks.map(task => {
      val rawEndDate = task.bpmn_scheduled_end_date[Long]
      val endDate = if (rawEndDate == -1) {
        "Not available"
      } else {
        val timeZone = user.tz[String]
        dateTimeString(task.bpmn_scheduled_end_date[Long], Some(timeZone))
      }
      val taskOid = task._id[ObjectId]
      val displayStatus = taskStatusValues(taskOid)
      val scope = displayStatus.toLowerCase match {
        case "not-started" => "Future"
        case "completed" | "ended" => "Past"
        case _ => "Current"
      }
      val taskName = task.get[String]("full_path_name") match {
        case Some(fpn) => fpn
        case None => task.name[String]
      }
      Map("_id" -> taskOid.toString, "name" -> taskName, "bpmn_name" -> task.bpmn_name[String],
        "status" -> displayStatus, "due_date" -> endDate, "scope" -> scope)
    })
    taskRecords.asJava
  }

  private def phaseDatesAndDurations(phase: DynDoc, editable: Boolean, status: String, request: HttpServletRequest):
      Seq[(String, Any)] = {
    val timestamps: DynDoc = phase.timestamps[Document]
    val user: DynDoc = getPersona(request)
    val timezone = user.tz[String]
    val estimatedStartDate = timestamps.get[Long]("estimated_start_date") match {
      case Some(dt) => dateString(dt, timezone)
      case None => "NA"
    }
    val estimatedFinishDate = timestamps.get[Long]("estimated_finish_date") match {
      case Some(dt) => dateString(dt, timezone)
      case None => "NA"
    }
    val actualStartDate = timestamps.get[Long]("actual_start_date") match {
      case Some(dt) => dateString(dt, timezone)
      case None => "NA"
    }
    val actualFinishDate = timestamps.get[Long]("actual_finish_date") match {
      case Some(dt) => dateString(dt, timezone)
      case None => "NA"
    }
    val phaseDuration = PhaseApi.allProcesses(phase).headOption match {
      case Some(process) =>
        val bpmnName = process.bpmn_name[String]
        ProcessBpmnTraverse.processDurationRecalculate(bpmnName, process, Seq.empty[(String, String, Int)], request).toString
      case None => "NA"
    }
    val startDateEditable = editable && status == "Not-Started"
    val finishDateEditable = editable && status != "Completed"
    Seq(
      ("estimated_start_date", new Document("editable", startDateEditable).append("value", estimatedStartDate)),
      ("estimated_finish_date", new Document("editable", finishDateEditable).append("value", estimatedFinishDate)),
      ("actual_start_date", new Document("editable", false).append("value", actualStartDate)),
      ("actual_finish_date", new Document("editable", false).append("value", actualFinishDate)),
      ("duration_optimistic", new Document("editable", false).append("value", "NA")),
      ("duration_pessimistic", new Document("editable", false).append("value", "NA")),
      ("duration_likely", new Document("editable", false).append("value", phaseDuration)),
      ("estimated_dates_editable", finishDateEditable)
    )
  }

  private def phaseKpis(phase: DynDoc): Many[Document] = {
    val ofOriginalBudget = "comment" -> "original budget"
    val originalBudget = phase.get[Any]("original_budget") match {
      case Some(budget) => budget.toString
      case None => "NA"
    }
    val kpis: Seq[DynDoc] = Seq(
      Map("name" -> "Original budget", "value" -> s"$originalBudget MM USD", "percent" -> "", "comment" -> ""),
      Map("name" -> "Current budget", "value" -> "1.65 MM USD", "percent" -> "115.3 %", ofOriginalBudget),
      Map("name" -> "Committed expense", "value" -> "0.75 MM USD", "percent" -> "49.3 %", ofOriginalBudget),
      Map("name" -> "Accrued expense", "value" -> "0.85 MM USD", "percent" -> "55.9 %", ofOriginalBudget),
      Map("name" -> "Paid expense", "value" -> "0.5 MM USD", "percent" -> "40.7 %", ofOriginalBudget),
      Map("name" -> "Change orders", "value" -> "0.05 MM USD", "percent" -> "5.5 %", ofOriginalBudget)
    )
    kpis.map(_.asDoc).asJava
  }

  private def phase2json(phase: DynDoc, user: DynDoc, request: HttpServletRequest): String = {
    val editable = isEditable(phase, user)
    val description = new Document("editable", editable).append("value", phase.description[String])
    val status = new Document("editable", false).append("value", phase.status[String])
    val rawDisplayStatus = PhaseApi.displayStatus3(phase)
    val displayStatus = new Document("editable", false).append("value", rawDisplayStatus)
    val name = new Document("editable", editable).append("value", phase.name[String])
    val rawPhaseManagers = phase.assigned_roles[Many[Document]].
        filter(_.role_name[String].matches("(?i)(Phase|Project)-Manager")).map(role => {
      val thePerson = PersonApi.personById(role.person_id[ObjectId])
      val personName = PersonApi.fullName(thePerson)
      new Document("_id", thePerson._id[ObjectId].toString).append("name", personName)
    }).asJava
    val phaseManagers = new Document("editable", editable).append("value", rawPhaseManagers)
    val rawGoals = phase.get[String]("goals") match {
      case None => s"Goals for '${phase.name[String]}'"
      case Some(theGoals) => theGoals
    }
    val goals = new Document("editable", editable).append("value", rawGoals)
    val bpmnName: String = PhaseApi.allProcesses(phase._id[ObjectId]).headOption match {
      case Some(theProcess) => theProcess.bpmn_name[String]
      case None => "not-available"
    }
    val userIsAdmin = PersonApi.isBuildWhizAdmin(Right(user))
    val deliverables = getDeliverables(phase, user)
    val deliverableInfo = deliverableInformation(deliverables)
    val phaseDoc = new Document("name", name).append("description", description).append("status", status).
        append("display_status", displayStatus).append("managers", phaseManagers).append("goals", goals).
        append("deliverable_info", deliverableInfo).append("display_edit_buttons", editable).
        append("task_info", taskInformation(deliverables, user)).append("bpmn_name", bpmnName).
        append("menu_items", displayedMenuItems(userIsAdmin, PhaseApi.canManage(user._id[ObjectId], phase)))
    phaseDatesAndDurations(phase, editable, rawDisplayStatus, request).foreach(pair => phaseDoc.append(pair._1, pair._2))
    phaseDoc.append("kpis", phaseKpis(phase))
    phaseDoc.toJson
  }

}