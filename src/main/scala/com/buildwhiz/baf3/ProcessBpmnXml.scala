package com.buildwhiz.baf3

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, BpmnUtils, DateTimeUtils, HttpUtils, ProjectUtils}
import org.bson.Document
import org.bson.types.ObjectId

import java.io.ByteArrayInputStream
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.annotation.tailrec
import scala.collection.mutable
import com.buildwhiz.baf2.{ActivityApi, OrganizationApi, PersonApi, PhaseApi, ProcessApi}

class ProcessBpmnXml extends HttpServlet with HttpUtils with BpmnUtils with DateTimeUtils with ProjectUtils {

  private def getVariables(process: DynDoc, bpmnFileName: String, bpmnNameFull: String): Seq[Document] = {
    val variables: Seq[DynDoc] = process.variables[Many[Document]].filter(
      v => if (v.has("bpmn_name_full")) {
        v.bpmn_name_full[String] == bpmnNameFull
      } else {
        v.bpmn_name[String] == bpmnFileName
      }
    )
    variables.map(variable => {
      variable.asDoc
    })
  }

  private def getMilestones(process: DynDoc, processName: String, bpmnNameFull: String): Seq[Document] = {
    val milestones: Seq[DynDoc] = process.get[Many[Document]]("milestones") match {
      case Some(ms) => ms.filter(
        milestone => if (milestone.has("bpmn_name_full")) {
          milestone.bpmn_name_full[String] == bpmnNameFull
        } else {
          milestone.bpmn_name[String] == processName
        }
      )
      case None => Seq.empty[DynDoc]
    }
    milestones.map(milestone => {
      new Document("bpmn_id", milestone.bpmn_id[String]).append("id", milestone.bpmn_id[String]).
          append("name", milestone.name[String]).
          append("start", milestone.start[String]).append("end", milestone.end[String]).
          append("status", milestone.status[String]).append("elementType", "milestone").
          //append("on_critical_path", if (milestone.has("on_critical_path")) milestone.on_critical_path[Boolean] else false).
          append("on_critical_path", false).
          append("offset", if (milestone.has("offset")) milestone.offset[Long].toString else "NA")
    })
  }

  private def getEndNodes(phase: DynDoc, process: DynDoc, processName: String, bpmnNameFull: String, cycleTime: Int,
      taktUnitNo: Int): Seq[Document] = {
    val endNodes: Seq[DynDoc] = process.get[Many[Document]]("end_nodes") match {
      case Some(ens) => ens.filter(
        endNode => if (endNode.has("bpmn_name_full")) {
          endNode.bpmn_name_full[String] == bpmnNameFull
        } else {
          endNode.bpmn_name[String] == processName
        }
      )
      case None => Seq.empty[DynDoc]
    }
    val phaseTimestamps: Option[DynDoc] = phase.get[Document]("timestamps")
    val phaseStartDate: Option[Long] = phaseTimestamps.flatMap(_.get[Long]("date_start_estimated"))
    endNodes.map(endNode => {
      val theOffset = endNode.get[Long]("offset").map(_ + cycleTime * (taktUnitNo - 1))
      val theDate = phaseStartDate.flatMap(startDate => theOffset.map(offset =>
        addWeekdays(startDate, math.max(0, offset - 1), PhaseApi.timeZone(phase)))) match {
        case Some(d) => dateTimeStringAmerican(d, Some(PhaseApi.timeZone(phase))).split(" ").head
        case None => "NA"
      }
      new Document("bpmn_id", endNode.bpmn_id[String]).append("id", endNode.bpmn_id[String]).
          append("name", endNode.name[String]).append("start", theDate).append("end", theDate).
          append("status", endNode.status[String]).append("elementType", "end_node").
          //append("on_critical_path", if (endNode.has("on_critical_path")) endNode.on_critical_path[Boolean] else false).
          append("on_critical_path", false).
          append("offset", if (endNode.has("offset")) endNode.offset[Long].toString else "NA")
    })
  }

  private def getStartNodes(phase: DynDoc, process: DynDoc, processName: String, bpmnNameFull: String, cycleTime: Int,
      taktUnitNo: Int): Seq[Document] = {
    val startNodes: Seq[DynDoc] = process.get[Many[Document]]("start_nodes") match {
      case Some(sns) => sns.filter(
        startNode => if (startNode.has("bpmn_name_full")) {
          startNode.bpmn_name_full[String] == bpmnNameFull
        } else {
          startNode.bpmn_name[String] == processName
        }
      )
      case None => Seq.empty[DynDoc]
    }
    val phaseTimestamps: Option[DynDoc] = phase.get[Document]("timestamps")
    val phaseStartDate: Option[Long] = phaseTimestamps.flatMap(_.get[Long]("date_start_estimated"))
    startNodes.map(startNode => {
      val theOffset = startNode.get[Long]("offset").map(_ + cycleTime * (taktUnitNo - 1))
      val theDate = phaseStartDate.flatMap(startDate => theOffset.map(offset =>
        addWeekdays(startDate, math.max(0, offset), PhaseApi.timeZone(phase)))) match {
        case Some(d) => dateTimeStringAmerican(d, Some(PhaseApi.timeZone(phase))).split(" ").head
        case None => "NA"
      }
      new Document("bpmn_id", startNode.bpmn_id[String]).append("id", startNode.bpmn_id[String]).
        append("name", startNode.name[String]).append("start", theDate).append("end", theDate).
        append("status", startNode.status[String]).append("elementType", "start_node").
        //append("on_critical_path", if (endNode.has("on_critical_path")) endNode.on_critical_path[Boolean] else false).
        append("on_critical_path", false).
        append("offset", if (startNode.has("offset")) startNode.offset[Long].toString else "NA")
    })
  }

  private def getTimers(process: DynDoc, processName: String, bpmnNameFull: String): Seq[Document] = {
    val timers: Seq[DynDoc] = process.timers[Many[Document]].filter(
      timer => if (timer.has("bpmn_name_full")) {
        timer.bpmn_name_full[String] == bpmnNameFull
      } else {
        timer.bpmn_name[String] == processName
      }
    )
    timers.map(timer => {
      val durationString = timer.duration[String]
      val durationDays = durationString.substring(0, durationString.indexOf(':')).toInt.toString
      new Document("bpmn_id", timer.bpmn_id[String]).append("id", timer.bpmn_id[String]).
        append("duration", durationString).append("duration_days", durationDays).append("name", timer.name[String]).
        append("start", timer.start[String]).append("end", timer.end[String]).
        append("status", timer.status[String]).append("elementType", "timer").
        //append("on_critical_path", if (timer.has("on_critical_path")) timer.on_critical_path[Boolean] else false).
        append("on_critical_path", false).append("bpmn_name", timer.bpmn_name[String])
    })
  }

  private def activityStartEndAndLabel(phase: DynDoc, activity: DynDoc, request: HttpServletRequest):
      ((String, String), (String, String)) = {
    val timezone = PhaseApi.timeZone(phase, Some(request))
    val unknownDatePattern = "__/__/____"

    val startAndLabel = ActivityApi.actualStart3(activity) match {
      case Some(start) => (dateTimeStringAmerican(start, Some(timezone)).split(" ").head, "Actual Start Date")
      case None => ActivityApi.scheduledStart31(phase, activity) match {
        case Some(start) => (dateTimeStringAmerican(start, Some(timezone)).split(" ").head, "Scheduled Start Date")
        case None => (unknownDatePattern, "Scheduled Start Date")
      }
    }

    val endAndLabel = ActivityApi.actualEnd3(activity) match {
      case Some(end) => (dateTimeStringAmerican(end, Some(timezone)).split(" ").head, "Actual End Date")
      case None => ActivityApi.scheduledEnd31(phase, activity) match {
        case Some(end) => (dateTimeStringAmerican(end, Some(timezone)).split(" ").head, "Scheduled End Date")
        case None => (unknownDatePattern, "Scheduled End Date")
      }
    }

    (startAndLabel, endAndLabel)
  }

  private def getSubProcessCalls(phase: DynDoc, process: DynDoc, processName: String, allActivities: Seq[DynDoc],
      bpmnNameFull: String): Seq[Document] = {
    val bpmnStamps: Seq[DynDoc] = process.bpmn_timestamps[Many[Document]].filter(
      stamp => if (stamp.has("bpmn_name_full2")) {
        stamp.bpmn_name_full2[String] == bpmnNameFull
      } else {
        stamp.parent_name[String] == processName
      }
    )
    val phaseTimestamps: Option[DynDoc] = phase.get[Document]("timestamps")
    val phaseStartDate: Option[Long] = phaseTimestamps.flatMap(_.get[Long]("date_start_estimated"))
    bpmnStamps.map(stamp => {
      val calledBpmnName = stamp.name[String]
      val bpmnNameFull = stamp.getOrElse("bpmn_name_full", "")
      val bpmnActivities = if (bpmnNameFull.isEmpty) {
        allActivities.filter(_.bpmn_name[String] == calledBpmnName)
      } else {
        allActivities.filter(_.bpmn_name_full[String] == bpmnNameFull)
      }
      val deliverables = DeliverableApi.deliverablesByActivityOids(bpmnActivities.map(_._id[ObjectId]))/*.
          filter(_.deliverable_type[String] != "Milestone")*/
      val deliverableCount = deliverables.length
      val uniqueStatusValues = DeliverableApi.taskStatusMap(deliverables).values.toSet
      val aggregatedStatus = uniqueStatusValues.size match {
        case 0 => "Unknown"
        case 1 => uniqueStatusValues.head
        case _ => "Current"
      }
      val startDate = "NA"
      val startLabel = "Scheduled Start Date"
      val endOffset: Option[Long] = stamp.get[Long]("offset_scheduled").
          flatMap(os => stamp.get[Long]("duration_scheduled").map(os + _))
      val endDate = phaseStartDate.flatMap(startDate => endOffset.map(dur =>
        addWeekdays(startDate, math.max(0, dur - 1), PhaseApi.timeZone(phase)))) match {
        case Some(d) => dateTimeStringAmerican(d, Some(PhaseApi.timeZone(phase))).split(" ").head
        case None => "__/__/____"
      }
      val endLabel = "Scheduled End Date"
      val hoverInfo = deliverables.map(d =>
        new Document("name", d.name[String]).append("status", d.status[String]).append("end_date", "__/__/____")
      )
      val durationLikely = stamp.get[Long]("duration_scheduled") match {
        case Some(d) => d.toString
        case None => "NA"
      }
      val isTakt = stamp.get[Boolean]("is_takt") match {
        case Some(taktValue) => taktValue
        case None => false
      }

      val milestoneInfo = allActivities.filter(_.getOrElse[Boolean]("is_milestone", false)).
        filter(_.bpmn_name_full[String].startsWith(bpmnNameFull)).
        map(a => (a, deliverables.find(d => d.activity_id[ObjectId] == a._id[ObjectId] && d.is_milestone[Boolean]))).
        map({
          case (task, Some(deliverable)) =>
            val endDateMs = deliverable.getOrElse[Long]("date_end_actual",
              deliverable.getOrElse[Long]("date_end_estimated", phaseStartDate.getOrElse(System.currentTimeMillis())))
            val endDate = dateTimeStringAmerican(endDateMs, Some(PhaseApi.timeZone(phase))).split(" ").head
            new Document("name", s"${task.name[String]} : ${task.takt_unit_no[Int]}").append("date_end", endDate).
              append("completed", deliverable.status[String].contains("Completed"))
          case (task, None) =>
            new Document("name", s"${task.name[String]} : ${task.takt_unit_no[Int]}").append("completed", false).
              append("date_end", "unknown")
        })

      new Document("bpmn_id", stamp.parent_activity_id[String]).append("id", stamp.name[String]).
        append("duration", durationLikely).
        append("start", startDate).append("end", endDate).append("status", aggregatedStatus).
        append("hover_info", hoverInfo).append("name", stamp.name[String]).append("elementType", "subprocessCall").
        append("date_start", startDate).append("date_finish", endDate).append("date_late_start", "NA").
        append("date_start_label", startLabel).append("date_end_label", endLabel).
        append("duration_optimistic", "NA").append("duration_pessimistic", "NA").
        append("duration_likely", durationLikely).append("is_takt", isTakt).
        //append("on_critical_path", if (stamp.has("on_critical_path")) stamp.on_critical_path[Boolean] else false).
        append("on_critical_path", false).append("deliverable_count", deliverableCount).
        append("bpmn_name_full", stamp.getOrElse[String]("bpmn_name_full", "")).
        append("milestone_info", milestoneInfo)
    })
  }

  private def getActivities(phase: DynDoc, processName: String, canManage: Boolean, bpmnNameFull: String,
      processActivities: Seq[DynDoc], request: HttpServletRequest): Seq[Document] = {
    val activities = processActivities.filter(
      activity => if (activity.has("bpmn_name_full")) {
        activity.bpmn_name_full[String] == bpmnNameFull
      } else {
        activity.bpmn_name[String] == processName
      }
    )
    val deliverables = DeliverableApi.deliverablesByActivityOids(activities.map(_._id[ObjectId])).
        filter(_.deliverable_type[String] != "Milestone")
    val activityStatusValues = DeliverableApi.taskStatusMap(deliverables)
    val returnActivities = activities.map(activity => {
      val activityOid = activity._id[ObjectId]
      val activityDeliverables = deliverables.filter(_.activity_id[ObjectId] == activityOid)
      val deliverableCount = activityDeliverables.length

      val ((activityStart, startLabel), (activityEnd, endLabel)) = activityStartEndAndLabel(phase, activity, request)

      val hoverInfo = activityDeliverables.map(d =>
        new Document("name", d.name[String]).append("status", d.status[String]).append("end_date", "__/__/____")
      )

      val assigneeInitials = ActivityApi.teamAssignment.list(activity._id[ObjectId]).
          find(_.role[String] == activity.role[String]) match {
        case None => "NA"
        case Some(assignment) => if (assignment.has("organization_id")) {
          val theOrganization = OrganizationApi.organizationById(assignment.organization_id[ObjectId])
          val orgNameParts = theOrganization.name[String].split("\\s+")
          val shortOrgName = (orgNameParts.head +: orgNameParts.tail.map(_.substring(0, 1))).mkString(" ")
          shortOrgName
        } else {
          "NA"
        }
      }

      val durationOptimistic = ActivityApi.durationOptimistic3(activity) match {
        case Some(value) => value.toString
        case None => "NA"
      }
      val durationPessimistic = ActivityApi.durationPessimistic3(activity) match {
        case Some(value) => value.toString
        case None => "NA"
      }
      val durationLikely = ActivityApi.durationLikely3(activity) match {
        case Some(value) => value.toString
        case None => "NA"
      }

      val description = activity.get[String]("description") match {
        case Some(d) => new Document("editable", canManage).append("value", d)
        case None => new Document("editable", canManage).append("value", "")
      }

      val status = activityStatusValues(activityOid)
      val isMilestone = activity.get[Boolean]("is_milestone") match {
        case None => false
        case Some(isMilestone) => isMilestone
      }
      val milestoneInfo = new Document("editable", canManage).append("is_milestone", isMilestone)
      if (isMilestone) {
        val phaseTimestamps: DynDoc = phase.timestamps[Document]
        val phaseStartDate = phaseTimestamps.getOrElse[Long]("date_start_estimated", System.currentTimeMillis())
        val (milestoneCompleted, milestoneDate) = activityDeliverables.find(_.is_milestone[Boolean]) match {
          case None => (false, "unknown")
          case Some(milestoneDeliverable) =>
            val endDateMs = milestoneDeliverable.getOrElse[Long]("date_end_actual",
              milestoneDeliverable.getOrElse[Long]("date_end_estimated", phaseStartDate))
            val milestoneEndDate = dateTimeStringAmerican(endDateMs, Some(PhaseApi.timeZone(phase))).split(" ").head
            (milestoneDeliverable.status[String].contains("Completed"), milestoneEndDate)
        }
        milestoneInfo.append("completed", milestoneCompleted).append("date_end", milestoneDate)
      }
      new Document("id", activity._id[ObjectId]).append("bpmn_id", activity.bpmn_id[String]).
        append("tasks", Seq.empty[Document]).append("start", activityStart).append("end", activityEnd).
        append("status", status).append("duration_is_editable", status != "Completed").
        append("duration", durationLikely).append("elementType", "activity").
        append("hover_info", hoverInfo).append("assignee_initials", assigneeInitials).
        append("milestone_info", milestoneInfo).
        append("name", activity.name[String]).append("bpmn_name", activity.bpmn_name[String]).
        append("duration_optimistic", durationOptimistic).append("duration_pessimistic", durationPessimistic).
        append("duration_likely", durationLikely).append("duration_actual", "NA").
        append("date_start", activityStart).append("date_finish", activityEnd).append("date_late_start", "NA").
        append("date_start_label", startLabel).append("date_end_label", endLabel).append("description", description).
        //append("on_critical_path", if (activity.has("on_critical_path")) activity.on_critical_path[Boolean] else false).
        append("on_critical_path", false).append("deliverable_count", deliverableCount).
        append("sub_zones", "1").append("start_delay", 0)
    })
    returnActivities
  }

  @tailrec
  private def bpmnAncestors(process: DynDoc, currentBpmnName: String, parents: Seq[String] = Nil): Seq[String] = {
    val parents2 = if (parents.isEmpty) {
      Seq(currentBpmnName)
    } else {
      parents
    }
    process.bpmn_timestamps[Many[Document]].find(ts => ts.name[String] == currentBpmnName &&
        ts.has("parent_name") && ts.parent_name[String].trim.nonEmpty) match {
      case None => parents2
      case Some(ts: DynDoc) =>
        val parentActivityName = ts.get[String]("parent_activity_name") match {
          case Some(pan) => pan.replaceAll("\\s+", " ")
          case None => ts.parent_name[String]
        }
        bpmnAncestors(process, ts.parent_name[String], parentActivityName +: parents2)
    }
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    val t0 = System.currentTimeMillis()
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val user: DynDoc = getPersona(request)
      //val userOid = user._id[ObjectId]
      val bpmnFileName = parameters("bpmn_name").replaceAll(" ", "-")
      val bpmnNameFull = parameters.getOrElse("bpmn_name_full", bpmnFileName)
      val (thePhase: DynDoc, theProcess: DynDoc) = (parameters.get("phase_id"), parameters.get("process_id")) match {
        case (None, Some(processOid)) =>
          val process = ProcessApi.processById(new ObjectId(processOid))
          val phase = ProcessApi.parentPhase(process._id[ObjectId])
          (phase, process)
        case (Some(phaseId), None) =>
          val phase = PhaseApi.phaseById(new ObjectId(phaseId))
          val process: DynDoc = PhaseApi.allProcesses(phase).headOption match {
            case Some(p) => p
            case None => throw new IllegalArgumentException("Phase has no processes")
          }
          (phase, process)
        case _ => throw new IllegalArgumentException("Ambiguous id combination in input")
      }
      val phaseOid = thePhase._id[ObjectId]
      val (globalTakt, taktUnitNo) = (parameters.get("is_takt"), parameters.get("takt_unit_no")) match {
        case (Some(taktValue), Some(taktUnitValue)) => (taktValue.toBoolean, taktUnitValue.toInt)
        case (Some(taktValue), None) => (taktValue.toBoolean, 1)
        case (None, _) => (false, 1)
      }
      val canManage = PhaseApi.canManage(user._id[ObjectId], thePhase)
      val processModelStream = if (bpmnFileName == "****") {
        new ByteArrayInputStream(placeholder.getBytes)
      } else {
        val version = theProcess.get[Int]("process_version") match {
          case Some(v) => v
          case None => 1
        }
        getProcessModel(bpmnFileName, version)
      }
      val blockBuffer = new Array[Byte](4096)
      val byteBuffer = mutable.Buffer.empty[Byte]
      @tailrec
      def copyModelToOutput(): Unit = {
        val len = processModelStream.read(blockBuffer)
        if (len > 0) {
          byteBuffer.appendAll(blockBuffer.take(len))
          copyModelToOutput()
        }
      }
      copyModelToOutput()
      val xml = new String(byteBuffer.toArray)
      val processVariables = getVariables(theProcess, bpmnFileName, bpmnNameFull)
      val processTimers = getTimers(theProcess, bpmnFileName, bpmnNameFull)
      val milestones = getMilestones(theProcess, bpmnFileName, bpmnNameFull)
      val allActivities = ActivityApi.activitiesByIds(theProcess.activity_ids[Many[ObjectId]])
      val perTaktUnitActivities = allActivities.filter(_.takt_unit_no[Int] == taktUnitNo)
      val processActivities = getActivities(thePhase, bpmnFileName, canManage, bpmnNameFull, perTaktUnitActivities, request)
      val repetitionCount = PhaseApi.getTaktUnitCount(phaseOid, bpmnNameFull, processActivities.length)
      val processCalls = getSubProcessCalls(thePhase, theProcess, bpmnFileName, allActivities,
        bpmnNameFull)
      val startDateTime: String = if (theProcess.has("timestamps")) {
        val timestamps: DynDoc = theProcess.timestamps[Document]
        if (timestamps.has("planned_start"))
          dateTimeString(timestamps.planned_start[Long], Some(user.tz[String])).split(" ").head
        else
          ""
      } else {
        ""
      }
      val parentBpmnName = theProcess.bpmn_timestamps[Many[Document]].find(ts => ts.name[String] == bpmnFileName) match {
        case None => ""
        case Some(ts: DynDoc) => ts.parent_name[String]
      }
      val bpmnDuration = ProcessBpmnTraverse.processDurationRecalculate(bpmnFileName, theProcess, 0, bpmnNameFull,
        Seq.empty[(String, String, Int)], request)
      val isAdmin = PersonApi.isBuildWhizAdmin(Right(user))
      val hostName = getHostName(request)
      val menuItems = uiContextSelectedManaged(request) match {
        case None => displayedMenuItems(isAdmin, request, starting = true)
        case Some((selected, managed)) => displayedMenuItems(isAdmin, request, managed, !selected)
      }
      val cycleTime = if (globalTakt) {
        processActivities.head.getString("duration")
      } else {
        ""
      }
      val safeCycleTime = try {
        cycleTime.toInt
      } catch {
        case _: Throwable => 0
      }
      val endNodes = getEndNodes(thePhase, theProcess, bpmnFileName, bpmnNameFull, safeCycleTime, taktUnitNo)
      val startNodes = getStartNodes(thePhase, theProcess, bpmnFileName, bpmnNameFull, safeCycleTime, taktUnitNo)
      val returnValue = new Document("xml", xml).append("variables", processVariables).
          append("timers", processTimers).append("activities", processActivities).append("calls", processCalls).
          append("admin_person_id", theProcess.admin_person_id[ObjectId]).append("start_datetime", startDateTime).
          append("process_status", theProcess.status[String]).append("parent_bpmn_name", parentBpmnName).
          append("bpmn_ancestors", bpmnAncestors(theProcess, bpmnFileName)).append("milestones", milestones).
          append("end_nodes", endNodes).append("bpmn_duration", bpmnDuration.toString).append("is_takt", globalTakt).
          append("repetition_count", repetitionCount).append("cycle_time", cycleTime).append("menu_items", menuItems).
          append("bpmn_name_full", bpmnNameFull).append("start_nodes", startNodes).
          append("activity_count", processActivities.length)
      response.getWriter.println(bson2json(returnValue))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      val delay = System.currentTimeMillis() - t0
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (time: $delay ms)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  private val placeholder =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="1.8.0">
      |  <bpmn:process id="Process_1" isExecutable="false">
      |    <bpmn:task id="Placeholder-430-Forest" name="Placeholder 430-Forest ">
      |      <bpmn:incoming>SequenceFlow_1ev0prc</bpmn:incoming>
      |      <bpmn:outgoing>SequenceFlow_1ox3c8r</bpmn:outgoing>
      |    </bpmn:task>
      |    <bpmn:startEvent id="Start">
      |      <bpmn:outgoing>SequenceFlow_1ev0prc</bpmn:outgoing>
      |    </bpmn:startEvent>
      |    <bpmn:endEvent id="End">
      |      <bpmn:incoming>SequenceFlow_1ox3c8r</bpmn:incoming>
      |    </bpmn:endEvent>
      |    <bpmn:sequenceFlow id="SequenceFlow_1ev0prc" sourceRef="Start" targetRef="Placeholder-430-Forest" />
      |    <bpmn:sequenceFlow id="SequenceFlow_1ox3c8r" sourceRef="Placeholder-430-Forest" targetRef="End" />
      |  </bpmn:process>
      |  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
      |    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      |      <bpmndi:BPMNShape id="Task_0d8635t_di" bpmnElement="Placeholder-430-Forest">
      |        <dc:Bounds x="441" y="135" width="100" height="80" />
      |      </bpmndi:BPMNShape>
      |      <bpmndi:BPMNShape id="StartEvent_08l5a8o_di" bpmnElement="Start">
      |        <dc:Bounds x="345" y="157" width="36" height="36" />
      |        <bpmndi:BPMNLabel>
      |          <dc:Bounds x="363" y="196" width="0" height="13" />
      |        </bpmndi:BPMNLabel>
      |      </bpmndi:BPMNShape>
      |      <bpmndi:BPMNShape id="EndEvent_13f5x8e_di" bpmnElement="End">
      |        <dc:Bounds x="607" y="157" width="36" height="36" />
      |        <bpmndi:BPMNLabel>
      |          <dc:Bounds x="625" y="196" width="0" height="13" />
      |        </bpmndi:BPMNLabel>
      |      </bpmndi:BPMNShape>
      |      <bpmndi:BPMNEdge id="SequenceFlow_1ev0prc_di" bpmnElement="SequenceFlow_1ev0prc">
      |        <di:waypoint xsi:type="dc:Point" x="381" y="175" />
      |        <di:waypoint xsi:type="dc:Point" x="441" y="175" />
      |        <bpmndi:BPMNLabel>
      |          <dc:Bounds x="411" y="153" width="0" height="13" />
      |        </bpmndi:BPMNLabel>
      |      </bpmndi:BPMNEdge>
      |      <bpmndi:BPMNEdge id="SequenceFlow_1ox3c8r_di" bpmnElement="SequenceFlow_1ox3c8r">
      |        <di:waypoint xsi:type="dc:Point" x="541" y="175" />
      |        <di:waypoint xsi:type="dc:Point" x="607" y="175" />
      |        <bpmndi:BPMNLabel>
      |          <dc:Bounds x="574" y="153" width="0" height="13" />
      |        </bpmndi:BPMNLabel>
      |      </bpmndi:BPMNEdge>
      |    </bpmndi:BPMNPlane>
      |  </bpmndi:BPMNDiagram>
      |</bpmn:definitions>
      |""".stripMargin
}
