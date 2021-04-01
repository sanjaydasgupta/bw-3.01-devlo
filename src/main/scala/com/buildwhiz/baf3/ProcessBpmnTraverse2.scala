package com.buildwhiz.baf3

import com.buildwhiz.baf2.{ActivityApi, PhaseApi}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils._
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.model.bpmn.instance._

import javax.servlet.http.HttpServletRequest
import scala.collection.JavaConverters._

object ProcessBpmnTraverse2 extends HttpUtils with DateTimeUtils with ProjectUtils with BpmnUtils {

  private def getTimerDuration(ted: TimerEventDefinition, process: DynDoc, bpmnName: String,
      durations: Seq[(String, String, Int)]): Long = {
    val timerDurations = durations.filter(_._1 == "T").map(t => (t._2, t._3)).toMap
    val theTimer: DynDoc = process.timers[Many[Document]].filter(_.bpmn_name[String] == bpmnName).
      filter(_.bpmn_id[String] == ted.getParentElement.getAttributeValue("id")).head
    if (timerDurations.contains(theTimer.bpmn_id[String])) {
      duration2ms(theTimer.duration[String])
    } else {
      theTimer.get[String]("duration") match {
        case Some(duration) => duration.substring(duration.indexOf(':')).toInt
        case None => 0
      }
    }
  }

  private def getActivityDuration(bpmnId: String, process: DynDoc, bpmnName: String,
      durations: Seq[(String, String, Int)]): Long = {
    val activityOids: Seq[ObjectId] = process.activity_ids[Many[ObjectId]]
    val theActivity: DynDoc = BWMongoDB3.activities.
      find(Map("_id" -> Map("$in" -> activityOids), "bpmn_name" -> bpmnName, "bpmn_id" -> bpmnId)).head
    val activityDurations = durations.filter(_._1 == "A").map(t => (new ObjectId(t._2), t._3)).toMap
    if (activityDurations.contains(theActivity._id[ObjectId])) {
      activityDurations(theActivity._id[ObjectId])
    } else {
      ActivityApi.durationLikely3(theActivity) match {
        case Some(days) => days
        case None => 0
      }
    }
  }

  def processDurationRecalculate(bpmnName: String, phaseOid: ObjectId, durations: Seq[(String, String, Int)],
      request: HttpServletRequest): Long = {

    val process = PhaseApi.allProcesses(phaseOid) match {
      case Seq(soloProcess) => soloProcess
      case _ => throw new IllegalArgumentException("Bad phase: mis-configured processes")
    }

    val duration = processDurationRecalculate(bpmnName, process, durations, request)
    duration
  }

  def processDurationRecalculate(bpmnName: String, process: DynDoc, durations: Seq[(String, String, Int)],
      request: HttpServletRequest): Long = {

    def getTimeOffset(node: FlowNode, bpmnName: String, onCriticalPath: Boolean, seenNodes: Set[FlowNode]): Long = {

      def predecessors(flowNode: FlowNode): Seq[FlowNode] = {
        val previousNodes: Seq[FlowNode] = flowNode.getPreviousNodes.list().asScala
        val unseenPredecessors = previousNodes.diff(seenNodes.toSeq)
        unseenPredecessors
      }

//      def predecessors(flowNode: FlowNode): Seq[FlowNode] = {
//        val incomingFlows: Seq[SequenceFlow] = flowNode.getIncoming.asScala.toSeq
//        val allPredecessors = incomingFlows.map(f => if (f.getTarget == flowNode) f.getSource else f.getTarget)
//        val unseenPredecessors = allPredecessors.diff(seenNodes.toSeq)
//        unseenPredecessors
//      }
//
      def maxPredecessorOffset(flowNode: FlowNode) = predecessors(flowNode).
          map(n => getTimeOffset(n, bpmnName, onCriticalPath, seenNodes + flowNode)).max

      def minPredecessorOffset(flowNode: FlowNode) = predecessors(flowNode).
          map(n => getTimeOffset(n, bpmnName, onCriticalPath, seenNodes + flowNode)).min

      val timeOffset = node match {
        case userTask: UserTask =>
          maxPredecessorOffset(userTask) + getActivityDuration(userTask.getId, process, bpmnName, durations)
        case serviceTask: ServiceTask =>
          maxPredecessorOffset(serviceTask)
        case _: StartEvent =>
          0
        case parallelGateway: ParallelGateway =>
          maxPredecessorOffset(parallelGateway)
        case exclusiveGateway: ExclusiveGateway =>
          minPredecessorOffset(exclusiveGateway)
        case endEvent: EndEvent =>
          maxPredecessorOffset(endEvent)
        case callActivity: CallActivity =>
          val calledElement = callActivity.getCalledElement
          val calledBpmnDuration = if (calledElement == "Infra-Activity-Handler") {
            getActivityDuration(callActivity.getId, process, bpmnName, durations)
          } else {
            val bpmnModel2 = bpmnModelInstance(calledElement)
            val endEvents: Seq[EndEvent] = bpmnModel2.getModelElementsByType(classOf[EndEvent]).asScala.toSeq
            getTimeOffset(endEvents.head, calledElement, onCriticalPath, seenNodes + callActivity)
          }
          maxPredecessorOffset(callActivity) + calledBpmnDuration
        case ice: IntermediateCatchEvent if !ice.getChildElementsByType(classOf[TimerEventDefinition]).isEmpty =>
          val ted = ice.getChildElementsByType(classOf[TimerEventDefinition]).asScala.head
          val delay = getTimerDuration(ted, process, bpmnName, durations)
          maxPredecessorOffset(ice) + delay
        case anyOtherNode: FlowNode =>
          maxPredecessorOffset(anyOtherNode)
      }
      //BWLogger.log(getClass.getName, "getTimeOffset",
      //    s"INFO: node: ${node.getName}(${node.getElementType.getTypeName}), offset: $timeOffset", request)
      timeOffset
    }

    val bpmnModel = bpmnModelInstance(bpmnName)
    val endEvents: Seq[EndEvent] = bpmnModel.getModelElementsByType(classOf[EndEvent]).asScala.toSeq
    if (endEvents.length > 1)
      BWLogger.log(getClass.getName, "processDurationRecalculate",
         s"WARN: found ${endEvents.length} EndEvent nodes in BPMN", request)
    val offset = getTimeOffset(endEvents.head, bpmnName, onCriticalPath = true, Set.empty[FlowNode])
    offset
  }

}
