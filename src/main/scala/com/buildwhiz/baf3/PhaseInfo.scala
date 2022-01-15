package com.buildwhiz.baf3

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId
import com.buildwhiz.baf2.{ActivityApi, PersonApi, PhaseApi, ProcessApi}

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.collection.JavaConverters._
import scala.math.random

class PhaseInfo extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val t0 = System.currentTimeMillis()
    val parameters = getParameterMap(request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
      val phaseRecord: DynDoc = PhaseApi.phaseById(phaseOid)
      val user: DynDoc = getPersona(request)
      response.getWriter.print(PhaseInfo.phase2json(phaseRecord, user, request: HttpServletRequest))
      val canManage = PhaseApi.canManage(user._id[ObjectId], phaseRecord)
      uiContextSelectedManaged(request, Some((true, canManage)))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      val delay = System.currentTimeMillis() - t0
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (time: $delay ms)", request)
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

  private def getDeliverables(phase: DynDoc, user: DynDoc, request: HttpServletRequest): Seq[DynDoc] = {
    val t0 = System.currentTimeMillis()
    val phaseTeamOids = PhaseApi.allTeamOids30(phase).toSet
    val myTeams = TeamApi.teamsByMemberOid(user._id[ObjectId])
    val myPhaseTeams = myTeams.filter(team => phaseTeamOids.contains(team._id[ObjectId]))
    val phaseActivityOids: Seq[ObjectId] = PhaseApi.allProcesses(phase).headOption match {
      case Some(process) => ProcessApi.allActivities(process).map(_._id[ObjectId])
      case None => Seq.empty
    }
    val deliverables = BWMongoDB3.deliverables.find(Map("activity_id" -> Map($in -> phaseActivityOids),
      "team_assignments" -> Map($elemMatch -> Map("team_id" -> Map($in -> myPhaseTeams.map(_._id[ObjectId]))))))
    val delay = System.currentTimeMillis() - t0
    BWLogger.log(getClass.getName, request.getMethod, s"getDeliverables() time: $delay ms", request)
    deliverables
  }

  private def deliverableInformation(deliverables: Seq[DynDoc], user: DynDoc, request: HttpServletRequest): Many[Document] = {
    val t0 = System.currentTimeMillis()
    val deliverableRecords: Seq[Document] = deliverables.map(deliverable => {
      val status = deliverable.status[String]
      val scope = status match {
        case "Not-Started" => "Future"
        case "Completed" | "Ended" => "Past"
        case _ => "Current"
      }
      val teamAssignments: Seq[DynDoc] = deliverable.team_assignments[Many[Document]]
      val (teamName, teamRole) = teamAssignments match {
        case Seq(teamAssignment) =>
          val team = TeamApi.teamById(teamAssignment.team_id[ObjectId])
          (team.team_name[String], teamAssignment.role[String])
        case ta: Seq[DynDoc] if ta.length > 1 =>
          val teamOids = teamAssignments.map(_.team_id[ObjectId])
          val teamsHavingMembers = TeamApi.teamsByIds(teamOids).filter(_.has("team_members"))
          val teamsWithPersonOids: Seq[(DynDoc, Seq[ObjectId])] =
              teamsHavingMembers.map(at => (at, at.team_members[Many[Document]].map(_.person_id[ObjectId])))
          val myTeam = teamsWithPersonOids.find(_._2.contains(user._id[ObjectId])).head._1
          val teamAssignment = teamAssignments.find(_.team_id[ObjectId] == myTeam._id[ObjectId]).head
          (myTeam.team_name[String], teamAssignment.role[String])
        case _ => // Not possible, keeps compiler happy!
      }
      val (weekOffset, commitDate): (Int, String) = deliverable.get[Long]("commit_date_value") match {
        case Some(commitDate) =>
          (math.round((commitDate - System.currentTimeMillis) / (7.0 * 86400000)).toInt, dateString(commitDate))
        case None =>
          (52, "Not available")
      }
      val externalStatus = DeliverableApi.externalStatusMapWithDefault(status)
      Map("_id" -> deliverable._id[ObjectId].toString, "activity_id" -> deliverable.activity_id[ObjectId].toString,
          "name" -> deliverable.name[String], "status" -> externalStatus, "due_date" -> "Not Available",
          "scope" -> scope, "team_name" -> teamName, "team_role" -> teamRole, "week_offset" -> weekOffset,
          "commit_date" -> commitDate)
    })
    val delay = System.currentTimeMillis() - t0
    BWLogger.log(getClass.getName, request.getMethod, s"deliverableInformation() time: $delay ms", request)
    deliverableRecords.asJava
  }

  private def taskInformation(deliverables: Seq[DynDoc], phase: DynDoc, request: HttpServletRequest): Many[Document] = {
    val taskStatusValues = DeliverableApi.taskStatusMap(deliverables)
    val activityOids = deliverables.map(_.activity_id[ObjectId]).distinct
    val tasks = ActivityApi.activitiesByIds(activityOids)
    val taskRecords: Seq[Document] = tasks.map(task => {
      val rawEndDate = task.bpmn_scheduled_end_date[Long]
      val endDate = if (rawEndDate == -1) {
        "Not available"
      } else {
        val timeZone = PhaseApi.timeZone(phase, Some(request))
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
    val t0 = System.currentTimeMillis()
    val timestamps: DynDoc = phase.timestamps[Document]
    val timezone = PhaseApi.timeZone(phase, Some(request))
    val estimatedStartDate = timestamps.get[Long]("date_start_estimated") match {
      case Some(dt) => dateString(dt, timezone)
      case None => "NA"
    }
    val estimatedEndDate = timestamps.get[Long]("date_end_estimated") match {
      case Some(dt) => dateString(dt, timezone)
      case None => "NA"
    }
    val actualStartDate = timestamps.get[Long]("date_start_actual") match {
      case Some(dt) => dateString(dt, timezone)
      case None => "NA"
    }
    val actualEndDate = timestamps.get[Long]("date_end_actual") match {
      case Some(dt) => dateString(dt, timezone)
      case None => "NA"
    }
    val phaseDuration = PhaseApi.allProcesses(phase).headOption match {
      case Some(process) =>
        val bpmnName = process.bpmn_name[String]
        ProcessBpmnTraverse.processDurationRecalculate(bpmnName, process, 0, bpmnName, Seq.empty[(String, String, Int)],
            request).toString
      case None => "NA"
    }
    val estimatedDatesEditable = editable && status == "Planning"
    val delay = System.currentTimeMillis() - t0
    BWLogger.log(getClass.getName, request.getMethod, s"phaseDatesAndDurations() time: $delay ms", request)
    Seq(
      ("date_start_estimated", new Document("editable", estimatedDatesEditable).append("value", estimatedStartDate)),
      ("date_end_estimated", new Document("editable", estimatedDatesEditable).append("value", estimatedEndDate)),
      ("date_start_actual", new Document("editable", false).append("value", actualStartDate)),
      ("date_end_actual", new Document("editable", false).append("value", actualEndDate)),
      ("duration_optimistic", new Document("editable", false).append("value", "NA")),
      ("duration_pessimistic", new Document("editable", false).append("value", "NA")),
      ("duration_likely", new Document("editable", false).append("value", phaseDuration)),
      ("estimated_dates_editable", estimatedDatesEditable)
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

  private def rint(): String = (random() * 15).toInt.toString

  private def phase2json(phase: DynDoc, user: DynDoc, request: HttpServletRequest): String = {
    val editable = isEditable(phase, user)
    val description = new Document("editable", editable).append("value", phase.description[String])
    val status = new Document("editable", false).append("value", phase.status[String])
    val rawDisplayStatus = PhaseApi.displayStatus31(phase)
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
    val deliverables = getDeliverables(phase, user, request)
    val deliverableInfo = deliverableInformation(deliverables, user, request)
    val counters: Document =
      Map[String, String]("alert_count" -> rint(), "rfi_count" -> rint(), "issue_count" -> rint())
    val timestamps: DynDoc = phase.timestamps[Document]
    val estimatedDatesExist = timestamps.has("date_start_estimated") && timestamps.has("date_end_estimated")
    val displayStartButton = editable && estimatedDatesExist &&
        (phase.get[Boolean]("started") match {case Some(sv) => !sv; case None => true})
    val phaseDoc = new Document("name", name).append("description", description).append("status", status).
        append("display_status", displayStatus).append("managers", phaseManagers).append("goals", goals).
        append("deliverable_info", deliverableInfo).append("display_edit_buttons", editable).
        append("display_start_button", displayStartButton).append("counters", counters).
        append("task_info", taskInformation(deliverables, phase, request)).append("bpmn_name", bpmnName).
        append("menu_items", displayedMenuItems(userIsAdmin, PhaseApi.canManage(user._id[ObjectId], phase)))
    phaseDatesAndDurations(phase, editable, rawDisplayStatus, request).foreach(pair => phaseDoc.append(pair._1, pair._2))
    phaseDoc.append("kpis", phaseKpis(phase))
    phaseDoc.toJson
  }

}