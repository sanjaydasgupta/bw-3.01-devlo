package com.buildwhiz.baf2

import java.io.ByteArrayInputStream

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.annotation.tailrec
import scala.collection.mutable

class ProcessBpmnXml extends HttpServlet with HttpUtils with BpmnUtils with DateTimeUtils with ProjectUtils {

  private def getVariables(process: DynDoc, bpmnFileName: String): Seq[Document] = {
    val variables: Seq[DynDoc] = process.variables[Many[Document]].filter(_.bpmn_name[String] == bpmnFileName)
    variables.map(variable => {
      variable.asDoc
    })
  }

  private def getTimers(process: DynDoc, processName: String): Seq[Document] = {
    val timers: Seq[DynDoc] = process.timers[Many[Document]].filter(_.bpmn_name[String] == processName)
    timers.map(timer => {
      new Document("bpmn_id", timer.bpmn_id[String]).append("id", timer.bpmn_id[String]).
        append("duration", timer.duration[String]).append("name", timer.name[String]).
        append("start", timer.start[String]).append("end", timer.end[String]).
        append("status", timer.status[String]).append("elementType", "timer").append("elementType", "timer").
        append("on_critical_path", if (timer.has("on_critical_path")) timer.on_critical_path[Boolean] else false)
    })
  }

  private def activityStartAndEndDate(activity: DynDoc, timezone: String): (String, String) = {
    val activityStart = ActivityApi.actualStart(activity) match {
      case Some(start) => dateTimeString(start, Some(timezone)).split(" ").head + " (A)"
      case None => ActivityApi.scheduledStart(activity) match {
        case Some(start) => dateTimeString(start, Some(timezone)).split(" ").head + " (S)"
        case None => "NA"
      }
    }

    val activityEnd = ActivityApi.actualEnd(activity) match {
      case Some(end) => dateTimeString(end, Some(timezone)).split(" ").head + " (A)"
      case None => ActivityApi.scheduledEnd(activity) match {
        case Some(end) => dateTimeString(end, Some(timezone)).split(" ").head + " (S)"
        case None => "NA"
      }
    }

    (activityStart, activityEnd)
  }

  private def getSubProcessCalls(process: DynDoc, processName: String, user: DynDoc, processActivities: Seq[DynDoc]):
      Seq[Document] = {
    val timezone = user.tz[String]
    val bpmnStamps: Seq[DynDoc] = process.bpmn_timestamps[Many[Document]].filter(_.parent_name[String] == processName)
    bpmnStamps.map(stamp => {
      val calledBpmnName = stamp.name[String]
      val bpmnActivities = processActivities.filter(_.bpmn_name[String] == calledBpmnName)
      val startAndEndDates = bpmnActivities.map(a => activityStartAndEndDate(a, timezone))
      val startDates = startAndEndDates.map(_._1).sorted
      val startDate = if (startDates.nonEmpty) startDates.head else "NA"
      val endDates = startAndEndDates.map(_._2).sorted
      val endDate = if (endDates.nonEmpty) endDates.last else "NA"
      val offset: DynDoc = stamp.offset[Document]
      val (start, end, status) = (offset.start[String], offset.end[String], stamp.status[String])
      val hoverInfo = Seq(
        new Document("name", "Start").append("value", startDate),
        new Document("name", "End").append("value", endDate),
        new Document("name", "Status").append("value", status),
      )

      new Document("bpmn_id", stamp.parent_activity_id[String]).append("id", stamp.name[String]).
        append("duration", ms2duration(duration2ms(end) - duration2ms(start))).
        append("start", startDate).append("end", endDate).append("status", stamp.status[String]).
        append("hover_info", hoverInfo).append("name", stamp.name[String]).append("elementType", "subprocessCall").
        append("on_critical_path", if (stamp.has("on_critical_path")) stamp.on_critical_path[Boolean] else false)
    })
  }

  private def getActivities(process: DynDoc, processName: String, user: DynDoc, processActivities: Seq[DynDoc]):
      Seq[Document] = {
    val activities = processActivities.filter(_.bpmn_name[String] == processName)
    val returnActivities = activities.map(activity => {
      val tasks = Seq.empty[Document]
      val status = if (tasks.exists(_.get("status") == "waiting"))
        "waiting"
      else if (tasks.exists(_.get("status") == "waiting2"))
        "waiting2"
      else
        activity.status[String]

      val timezone = user.tz[String]

      val (activityStart, activityEnd) = activityStartAndEndDate(activity, timezone)

      val assignments: Seq[DynDoc] = ActivityApi.teamAssignment.list(activity._id[ObjectId])

      val priority = Map("Pre-Approval" -> 1, "Post-Approval" -> 3)

      val assignmentInfo: Seq[Document] = assignments.sortBy(a => priority.getOrElse(a.role[String], 2)).
          zipWithIndex.flatMap(a => {
        val name = if (a._1.has("person_id")) {
          val personOid = a._1.person_id[ObjectId]
          val personRec = PersonApi.personById(personOid)
          PersonApi.fullName(personRec)
        } else
        "NA"
        val sno = a._2 + 1
        Seq(
          new Document("name", s"Role ($sno)").append("value", a._1.role[String]),
          new Document("name", s"Status ($sno)").append("value", a._1.status[String]),
          new Document("name", s"Person ($sno)").append("value", name)
        )
      })

      val hoverInfo = Seq(
        new Document("name", "Start").append("value", activityStart),
        new Document("name", "End").append("value", activityEnd),
        new Document("name", "Status").append("value", ActivityApi.stateSubState(activity))
      ) ++ assignmentInfo

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

      new Document("id", activity._id[ObjectId]).append("bpmn_id", activity.bpmn_id[String]).
        append("status", status).append("tasks", tasks).append("start", activityStart).append("end", activityEnd).
        append("duration", getActivityDuration(activity)).append("elementType", "activity").
        append("hover_info", hoverInfo).append("assignee_initials", assigneeInitials).
        append("name", activity.name[String]).append("bpmn_name", activity.bpmn_name[String]).
        append("on_critical_path", if (activity.has("on_critical_path")) activity.on_critical_path[Boolean] else false)
    })
    returnActivities
  }

  @tailrec
  private def bpmnAncestors(process: DynDoc, currentBpmnName: String, parents: Seq[String] = Nil): Seq[String] = {
    process.bpmn_timestamps[Many[Document]].find(ts => ts.name[String] == currentBpmnName &&
        ts.has("parent_name") && ts.parent_name[String].trim.nonEmpty) match {
      case None => parents
      case Some(ts: DynDoc) => bpmnAncestors(process, ts.parent_name[String], ts.parent_name[String] +: parents)
    }
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      //val userOid = user._id[ObjectId]
      val bpmnFileName = parameters("bpmn_name").replaceAll(" ", "-")
      val processOid = new ObjectId(parameters("process_id"))
      val processModelStream = if (bpmnFileName == "****")
        new ByteArrayInputStream(placeholder.getBytes)
      else
        getProcessModel(bpmnFileName)
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
      val process: DynDoc = BWMongoDB3.processes.find(Map("_id" -> processOid)).head
      val processVariables = getVariables(process, bpmnFileName)
      val processTimers = getTimers(process, bpmnFileName)
      val allActivities = ActivityApi.activitiesByIds(process.activity_ids[Many[ObjectId]])
      val processActivities = getActivities(process, bpmnFileName, user, allActivities)
      val processCalls = getSubProcessCalls(process, bpmnFileName, user, allActivities)
      val startDateTime: String = if (process.has("timestamps")) {
        val timestamps: DynDoc = process.timestamps[Document]
        if (timestamps.has("planned_start"))
          dateTimeString(timestamps.planned_start[Long], Some(user.tz[String])).split(" ").head
        else
          ""
      } else {
        ""
      }
      val parentBpmnName = process.bpmn_timestamps[Many[Document]].find(ts => ts.name[String] == bpmnFileName) match {
        case None => ""
        case Some(ts: DynDoc) => ts.parent_name[String]
      }
      val returnValue = new Document("xml", xml).append("variables", processVariables).
        append("timers", processTimers).append("activities", processActivities).append("calls", processCalls).
        append("admin_person_id", process.admin_person_id[ObjectId]).append("start_datetime", startDateTime).
        append("process_status", process.status[String]).append("parent_bpmn_name", parentBpmnName).
        append("bpmn_ancestors", bpmnAncestors(process, bpmnFileName))
      response.getWriter.println(bson2json(returnValue))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
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
