package com.buildwhiz.baf

import java.io.ByteArrayInputStream
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.utils._
import org.bson.types.ObjectId
import org.bson.Document
import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._

import scala.collection.mutable

class PhaseBpmnXml extends HttpServlet with HttpUtils with BpmnUtils with DateTimeUtils with ProjectUtils {

  private def getVariables(phase: DynDoc, processName: String): Seq[Document] = {
    val variables: Seq[DynDoc] = phase.variables[Many[Document]].filter(_.bpmn_name[String] == processName)
    variables.map(variable => {
      variable.asDoc
    })
  }

  private def getTimers(phase: DynDoc, processName: String): Seq[Document] = {
    val timers: Seq[DynDoc] = phase.timers[Many[Document]].filter(_.bpmn_name[String] == processName)
    timers.map(timer => {
      new Document("bpmn_id", timer.bpmn_id[String]).append("id", timer.bpmn_id[String]).
        append("duration", timer.duration[String]).append("name", timer.name[String]).
        append("start", timer.start[String]).append("end", timer.end[String]).
        append("status", timer.status[String])
    })
  }

  private def getSubProcessCalls(phase: DynDoc, processName: String): Seq[Document] = {
    val bpmnStamps: Seq[DynDoc] = phase.bpmn_timestamps[Many[Document]].filter(_.parent_name[String] == processName)
    bpmnStamps.map(stamp => {
      val offset: DynDoc = stamp.offset[Document]
      val (start, end) = (offset.start[String], offset.end[String])
      new Document("bpmn_id", stamp.parent_activity_id[String]).append("id", stamp.parent_activity_id[String]).
        append("duration", ms2duration(duration2ms(end) - duration2ms(start))).
        append("start", start).append("end", end).
        append("status", stamp.status[String]).append("name", stamp.name[String])
    })
  }

  private def getActivities(phase: DynDoc, processName: String, userOid: ObjectId): Seq[Document] = {
    val activityOids: Seq[ObjectId] = phase.activity_ids[Many[ObjectId]]
    val activities: Seq[DynDoc] = BWMongoDB3.activities.
      find(Map("_id" -> Map("$in" -> activityOids), "bpmn_name" -> processName))
    val returnActivities = activities.map(activity => {
      val actions: Seq[DynDoc] = activity.actions[Many[Document]]
      val tasks = actions.map(action => {
        val ownTask = userOid == action.assignee_person_id[ObjectId]
        val status = if (ownTask && action.status[String] == "waiting")
          "waiting"
        else if (action.status[String] == "waiting")
          "waiting2"
        else
          action.status[String]
        val assignee: DynDoc = BWMongoDB3.persons.find(Map("_id" -> action.assignee_person_id[ObjectId])).head
        val shortAssignee = new Document("_id", assignee._id[ObjectId]).
          append("name", s"${assignee.first_name[String]} ${assignee.last_name[String]}")
        new Document("type", action.`type`[String]).append("name", action.name[String]).append("status", status).
          append("duration", action.duration[String]).append("assignee", shortAssignee).
          append("start", action.start[String]).append("end", action.end[String])
      })
      val status = if (tasks.exists(_.get("status") == "waiting"))
        "waiting"
      else if (tasks.exists(_.get("status") == "waiting2"))
        "waiting2"
      else
        activity.status[String]
      new Document("id", activity._id[ObjectId]).append("bpmn_id", activity.bpmn_id[String]).
        append("status", status).append("tasks", tasks).
        append("start", activity.start[String]).append("end", activity.end[String]).
        append("duration", getActivityDuration(activity))
    })
    returnActivities
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      val bpmnFileName = parameters("bpmn_name").replaceAll(" ", "-")
      val phaseOid = new ObjectId(parameters("phase_id"))
      val processModelStream = if (bpmnFileName == "****")
        new ByteArrayInputStream(placeholder.getBytes)
      else
        getProcessModel(bpmnFileName)
      val blockBuffer = new Array[Byte](4096)
      val byteBuffer = mutable.Buffer.empty[Byte]
      def copyModelToOutput(): Unit = {
        val len = processModelStream.read(blockBuffer)
        if (len > 0) {
          byteBuffer.append(blockBuffer.take(len): _*)
          copyModelToOutput()
        }
      }
      copyModelToOutput()
      val xml = new String(byteBuffer.toArray)
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val processVariables = getVariables(phase, bpmnFileName)
      val processTimers = getTimers(phase, bpmnFileName)
      val processActivities = getActivities(phase, bpmnFileName, userOid)
      val processCalls = getSubProcessCalls(phase, bpmnFileName)
      val startDateTime = if (phase.has("timestamps.planned_start")) {
        val timestamps: DynDoc = phase.timestamps[Document]
        timestamps.planned_start[Long]
      } else {
        0
      }
      val returnValue = new Document("xml", xml).append("variables", processVariables).
        append("timers", processTimers).append("activities", processActivities).append("calls", processCalls).
        append("admin_person_id", phase.admin_person_id[ObjectId]).append("start_datetime", startDateTime)
      response.getWriter.println(bson2json(returnValue))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
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
