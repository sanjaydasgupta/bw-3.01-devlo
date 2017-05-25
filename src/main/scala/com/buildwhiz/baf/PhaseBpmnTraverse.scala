package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils._
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.model.bpmn.BpmnModelInstance
import org.camunda.bpm.model.bpmn.instance._

import scala.collection.JavaConverters._
import scala.collection.mutable

class PhaseBpmnTraverse extends HttpServlet with HttpUtils with BpmnUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val bpmnFileName = parameters("bpmn_name").replaceAll(" ", "-")
      val bpmnModel = BpmnModelInstance(bpmnFileName)
      val phaseOid = new ObjectId(parameters("phase_id"))
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
//      val processVariables = PhaseBpmnTraverse.getVariables(phase, bpmnFileName)
//      val processTimers = PhaseBpmnTraverse.getTimers(phase, bpmnFileName)
//      val processActivities = PhaseBpmnTraverse.getActivities(phase, bpmnFileName)
      PhaseBpmnTraverse.traverse(bpmnModel, phase, bpmnFileName, request, response)
//      val returnValue = new Document("variables", processVariables).
//        append("timers", processTimers).append("activities", processActivities)
//      response.getWriter.println(bson2json(returnValue))
//      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}

object PhaseBpmnTraverse extends HttpUtils with DateTimeUtils with ProjectUtils {

  private def getVariables(phase: DynDoc, processName: String): Seq[Document] = {
    val variables: Seq[DynDoc] = phase.variables[Many[Document]].filter(_.bpmn_name[String] == processName)
    variables.map(variable => {
      variable.asDoc
    })
  }

  private def getTimers(phase: DynDoc, processName: String): Seq[DynDoc] = {
    val timers: Seq[DynDoc] = phase.timers[Many[Document]].filter(_.bpmn_name[String] == processName)
    timers.map(timer => {
      timer.id = timer.bpmn_name[String]
      timer.remove("bpmn_name")
      timer.asDoc
    })
  }

  private def getActivities(phase: DynDoc, processName: String): Seq[Document] = {
    val activityOids: Seq[ObjectId] = phase.activity_ids[Many[ObjectId]]
    val activities: Seq[DynDoc] = BWMongoDB3.activities.
      find(Map("_id" -> Map("$in" -> activityOids), "bpmn_name" -> processName))
    val returnActivities = activities.map(activity => {
      val actions: Seq[DynDoc] = activity.actions[Many[Document]]
      val tasks = actions.map(action => {
        new Document("type", action.`type`[String]).append("name", action.name[String]).
          append("status", action.status[String]).append("duration", action.duration[String])
      })
      new Document("id", activity.bpmn_id[String]).append("status", activity.status[String]).append("tasks", tasks)
    })
    returnActivities
  }

  private def timerDuration(ted: TimerEventDefinition, phase: DynDoc, bpmnName: String): Long = {
    val theTimer: DynDoc = phase.timers[Many[Document]].filter(_.bpmn_name[String] == bpmnName).
      filter(_.bpmn_id[String] == ted.getParentElement.getAttributeValue("id")).head
    duration2ms(theTimer.duration[String])
  }

  private def activityDuration(bpmnId: String, phase: DynDoc, bpmnName: String): Long = {
    val activityOids: Seq[ObjectId] = phase.activity_ids[Many[ObjectId]]
    val activities: Seq[DynDoc] = BWMongoDB3.activities.
      find(Map("_id" -> Map("$in" -> activityOids), "bpmn_name" -> bpmnName))
    val theActivity: DynDoc = activities.filter(_.bpmn_id[String] == bpmnId).head
    duration2ms(getActivityDuration(theActivity))
  }

  private def traverse(bpmnModel: BpmnModelInstance, phase: DynDoc, bpmnName: String,
      request: HttpServletRequest, response: HttpServletResponse): Unit = {

    def predecessors(node: FlowNode): Seq[FlowNode] = {
      val incomingFlows: Seq[SequenceFlow] = node.getIncoming.asScala.toSeq
      incomingFlows.map(f => if (f.getTarget == node) f.getSource else f.getTarget)
    }

    def minMax(a: (Long, Long), b: (Long, Long)) = (math.min(a._1, b._1), math.max(a._2, b._2))
    def maxMax(a: (Long, Long), b: (Long, Long)) = (math.max(a._1, b._1), math.max(a._2, b._2))

    def timeOffset(node: FlowNode): (Long, Long) = node match {
      case serviceTask: ServiceTask =>
        predecessors(serviceTask).map(timeOffset).reduce(minMax)
      case _: StartEvent =>
        (0, 0)
      case parallelGateway: ParallelGateway =>
        predecessors(parallelGateway).map(timeOffset).reduce(maxMax)
      case exclusiveGateway: ExclusiveGateway =>
        predecessors(exclusiveGateway).map(timeOffset).reduce(minMax)
      case endEvent: EndEvent =>
        val incomingFlows: Seq[SequenceFlow] = endEvent.getIncoming.asScala.toSeq
        val predecessors = incomingFlows.map(f => if (f.getTarget == endEvent) f.getSource else f.getTarget)
        predecessors.map(timeOffset).reduce(minMax)
      case callActivity: CallActivity =>
        if (callActivity.getCalledElement == "Infra-Activity-Handler") {
          val predecessorOffset = predecessors(callActivity).map(timeOffset).reduce(minMax)
          val delay = activityDuration(callActivity.getId, phase, bpmnName)
          (predecessorOffset._1 + delay, predecessorOffset._2 + delay)
        } else {
          ???
        }
      case ice: IntermediateCatchEvent if !ice.getChildElementsByType(classOf[TimerEventDefinition]).isEmpty =>
        val predOffset = predecessors(ice).map(timeOffset).reduce(minMax)
        val ted = ice.getChildElementsByType(classOf[TimerEventDefinition]).asScala.head
        val delay = timerDuration(ted, phase, bpmnName)
        (predOffset._1 + delay, predOffset._2 + delay)
    }

/*    <bpmn2:intermediateCatchEvent id="Initial-Delay" name="Initial Delay">
      <bpmn2:extensionElements>
        <camunda:executionListener class="com.buildwhiz.jelly.TimerTransitions" event="start" />
        <camunda:executionListener class="com.buildwhiz.jelly.TimerTransitions" event="end" />
      </bpmn2:extensionElements>
      <bpmn2:incoming>SequenceFlow_3</bpmn2:incoming>
      <bpmn2:outgoing>SequenceFlow_2</bpmn2:outgoing>
      <bpmn2:timerEventDefinition id="TimerEventDefinition_1">
        <bpmn2:timeDuration xsi:type="bpmn2:tFormalExpression">${initial_delay}</bpmn2:timeDuration>
      </bpmn2:timerEventDefinition>
    </bpmn2:intermediateCatchEvent>

    <bpmn:callActivity id="Install-Rebars" name="Install Rebars" calledElement="Infra-Activity-Handler">
      <bpmn:extensionElements>
        <camunda:properties>
          <camunda:property name="bw-skill" value="33-41 31 00" />
          <camunda:property name="bw-sequence" value="1" />
        </camunda:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>SequenceFlow_0fl7orw</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_0wl2d4f</bpmn:outgoing>
    </bpmn:callActivity>
*/

    def stepBack(node: FlowNode, buffer: Seq[FlowNode] = Nil): Seq[FlowNode] = node match {
      case serviceTask: ServiceTask =>
        (serviceTask +: (predecessors(serviceTask).flatMap(t => stepBack(t)) ++ buffer)).distinct
      case startNode: StartEvent =>
        (startNode +: buffer).distinct
      case parallelGateway: ParallelGateway =>
        (parallelGateway +: (predecessors(parallelGateway).flatMap(t => stepBack(t)) ++ buffer)).distinct
      case exGateway: ExclusiveGateway =>
       (exGateway +: (predecessors(exGateway).flatMap(t => stepBack(t)) ++ buffer)).distinct
      case endEvent: EndEvent =>
        val incomingFlows: Seq[SequenceFlow] = endEvent.getIncoming.asScala.toSeq
        val targets = incomingFlows.map(f => if (f.getTarget == endEvent) f.getSource else f.getTarget)
        (endEvent +: (targets.flatMap(t => stepBack(t)) ++ buffer)).distinct
      case callActivity: CallActivity =>
        (callActivity +: (predecessors(callActivity).flatMap(t => stepBack(t)) ++ buffer)).distinct
      case intCatchEvent: IntermediateCatchEvent =>
        (intCatchEvent +: (predecessors(intCatchEvent).flatMap(t => stepBack(t)) ++ buffer)).distinct
    }
    val endEvents: Seq[FlowNode] = bpmnModel.getModelElementsByType(classOf[EndEvent]).asInstanceOf[Many[EndEvent]]
    //val allTheNodes: Seq[FlowNode] = stepBack(endEvents.head)
    val offset = timeOffset(endEvents.head)
    response.getWriter.println(bson2json(new Document("min", offset._1).append("max", offset._2)))
    response.setContentType("application/json")
  }

}
