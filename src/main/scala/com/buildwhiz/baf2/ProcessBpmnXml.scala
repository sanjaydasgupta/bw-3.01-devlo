package com.buildwhiz.baf2

import java.io.ByteArrayInputStream

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.mutable

class ProcessBpmnXml extends HttpServlet with HttpUtils with BpmnUtils with DateTimeUtils with ProjectUtils {

  private def getVariables(process: DynDoc, processName: String): Seq[Document] = {
    val variables: Seq[DynDoc] = process.variables[Many[Document]].filter(_.bpmn_name[String] == processName)
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

  private def getSubProcessCalls(process: DynDoc, processName: String): Seq[Document] = {
    val bpmnStamps: Seq[DynDoc] = process.bpmn_timestamps[Many[Document]].filter(_.parent_name[String] == processName)
    bpmnStamps.map(stamp => {
      val offset: DynDoc = stamp.offset[Document]
      val (start, end) = (offset.start[String], offset.end[String])
      val hoverInfo = Seq(
        new Document("name", "Start-Offset").append("value", start),
        new Document("name", "End-Offset").append("value", end),
        //new Document("name", "Status").append("value", ActivityApi.stateSubState(activity)),
      )

      new Document("bpmn_id", stamp.parent_activity_id[String]).append("id", stamp.name[String]).
        append("duration", ms2duration(duration2ms(end) - duration2ms(start))).
        append("start", start).append("end", end).append("status", stamp.status[String]).
        append("hover_info", hoverInfo).append("name", stamp.name[String]).append("elementType", "subprocessCall").
        append("on_critical_path", if (stamp.has("on_critical_path")) stamp.on_critical_path[Boolean] else false)
    })
  }

  private def getActivities(process: DynDoc, processName: String, user: DynDoc): Seq[Document] = {
    val activityOids: Seq[ObjectId] = process.activity_ids[Many[ObjectId]]
    val activities: Seq[DynDoc] = BWMongoDB3.activities.
      find(Map("_id" -> Map("$in" -> activityOids), "bpmn_name" -> processName))
    val returnActivities = activities.map(activity => {
      val actions: Seq[DynDoc] = activity.actions[Many[Document]]
      val tasks = actions.map(action => {
        val ownTask = user._id[ObjectId] == action.assignee_person_id[ObjectId]
        val status = if (ownTask && action.status[String] == "waiting")
          "waiting"
        else if (action.status[String] == "waiting")
          "waiting2"
        else
          action.status[String]
        val assignee: DynDoc = BWMongoDB3.persons.find(Map("_id" -> action.assignee_person_id[ObjectId])).head
        val shortAssignee = new Document("_id", assignee._id[ObjectId]).
          append("name", s"${assignee.first_name[String]} ${assignee.last_name[String]}")
        val actionType = action.`type`[String]
        val actionRole = if (action.has("assignee_role")) {
          action.assignee_role[String]
        } else if (action.has("role")) {
          action.role[String]
        } else if (activity.has("assignee_role")) {
          if (actionType == "main")
            activity.assignee_role[String]
          else
            s"${activity.assignee_role[String]}-$actionType"
        } else if (activity.has("role")) {
          if (actionType == "main")
            activity.role[String]
          else
            s"${activity.role[String]}-$actionType"
        } else {
          s"???-$actionType"
        }
        //val baseRole = if (action.has("role")) action.role[String] else activity.role[String]
        //val actionRole = if (actionType == "main") baseRole else s"$baseRole-$actionType"
        new Document("type", actionType).append("name", action.name[String]).append("status", status).
          append("duration", action.duration[String]).append("assignee", shortAssignee).
          append("start", action.start[String]).append("end", action.end[String]).append("role", actionRole).
          append("on_critical_path",
              if (action.has("on_critical_path")) action.on_critical_path[Boolean] else false)
      })
      val status = if (tasks.exists(_.get("status") == "waiting"))
        "waiting"
      else if (tasks.exists(_.get("status") == "waiting2"))
        "waiting2"
      else
        activity.status[String]

      val timezone = user.tz[String]

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

      val assignments: Seq[DynDoc] = ActivityApi.teamAssignment.list(activity._id[ObjectId])

      val priority = Map("Pre-Approval" -> 1, "Post-Approval" -> 3)

      val assignmentInfo: Seq[Document] = assignments.sortBy(a => priority.getOrElse(a.role[String], 2)).
          zipWithIndex.flatMap(a => {
        val name = if (a._1.has("person_id")) {
          val personOid = a._1.person_id[ObjectId]
          val personRec = PersonApi.personById(personOid)
          s"${personRec.first_name[String]} ${personRec.last_name[String]}"
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
        new Document("name", "Status").append("value", ActivityApi.stateSubState(activity)),
      ) ++ assignmentInfo

      val assigneeInitials = ActivityApi.teamAssignment.list(activity._id[ObjectId]).
          find(_.role[String] == activity.role[String]) match {
        case None => "NA"
        case Some(assignment) => if (assignment.has("person_id")) {
          val thePerson = PersonApi.personById(assignment.person_id[ObjectId])
          s"${thePerson.first_name[String].substring(0, 1)} ${thePerson.last_name[String].substring(0, 1)}"
        } else {
          "NA"
        }
      }

      new Document("id", activity._id[ObjectId]).append("bpmn_id", activity.bpmn_id[String]).
        append("status", status).append("tasks", tasks).append("start", activityStart).append("end", activityEnd).
        append("duration", getActivityDuration(activity)).append("elementType", "activity").
        append("hover_info", hoverInfo).append("assignee_initials", assigneeInitials).
        append("on_critical_path",
            if (activity.has("on_critical_path")) activity.on_critical_path[Boolean] else false)
    })
    returnActivities
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
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
      def copyModelToOutput(): Unit = {
        val len = processModelStream.read(blockBuffer)
        if (len > 0) {
          byteBuffer.append(blockBuffer.take(len): _*)
          copyModelToOutput()
        }
      }
      copyModelToOutput()
      val xml = new String(byteBuffer.toArray)
      val process: DynDoc = BWMongoDB3.processes.find(Map("_id" -> processOid)).head
      val processVariables = getVariables(process, bpmnFileName)
      val processTimers = getTimers(process, bpmnFileName)
      val processActivities = getActivities(process, bpmnFileName, user)
      val processCalls = getSubProcessCalls(process, bpmnFileName)
      val startDateTime = if (process.has("timestamps")) {
        val timestamps: DynDoc = process.timestamps[Document]
        if (timestamps.has("planned_start")) timestamps.planned_start[Long] else 0
      } else {
        0
      }
      val returnValue = new Document("xml", xml).append("variables", processVariables).
        append("timers", processTimers).append("activities", processActivities).append("calls", processCalls).
        append("admin_person_id", process.admin_person_id[ObjectId]).append("start_datetime", startDateTime).
        append("process_status", process.status[String])
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
