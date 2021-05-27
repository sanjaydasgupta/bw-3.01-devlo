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

  private def setMilestoneOffset(ted: IntermediateThrowEvent, process: DynDoc, bpmnName: String, offset: Long,
      request: HttpServletRequest): Unit = {
    //BWLogger.log(getClass.getName, request.getMethod, s"ENTRY: setMilestoneOffset()", request)
    val processOid = process._id[ObjectId]
    val name = ted.getAttributeValue("name")
    val id = ted.getAttributeValue("id")
    val query = Map("_id" -> processOid, "milestones" -> Map($elemMatch -> Map("bpmn_id" -> id, "bpmn_name" -> bpmnName,
      "name" -> name)))
    val setter = Map($set -> Map("milestones.$.offset" -> offset))
    val updateResult = BWMongoDB3.processes.updateOne(query, setter)
    if (updateResult.getMatchedCount == 0) {
      BWLogger.log(getClass.getName, request.getMethod,
          s"ERROR:setMilestoneOffset:query=$query, setter=$setter", request)
      //throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    }
    //BWLogger.log(getClass.getName, request.getMethod,
    //  s"EXIT-OK: setMilestoneOffset(updated=${updateResult.getModifiedCount}, bpmn_id=$id, name=$name)", request)
  }

  private def setEndEventOffset(endEvent: EndEvent, process: DynDoc, bpmnName: String, offset: Long,
      request: HttpServletRequest): Unit = {
    //BWLogger.log(getClass.getName, request.getMethod, s"ENTRY: setEndEventOffset()", request)
    val processOid = process._id[ObjectId]
    val name = endEvent.getAttributeValue("name")
    val id = endEvent.getAttributeValue("id")
    val query = Map("_id" -> processOid, "end_nodes" -> Map($elemMatch -> Map("bpmn_id" -> id, "bpmn_name" -> bpmnName,
      "name" -> name)))
    val setter = Map($set -> Map("end_notes.$.offset" -> offset))
    val updateResult = BWMongoDB3.processes.updateOne(query, setter)
    if (updateResult.getMatchedCount == 0) {
      BWLogger.log(getClass.getName, request.getMethod,
          s"ERROR: setEndEventOffset:query=$query, setter=$setter", request)
      //throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    }
    //BWLogger.log(getClass.getName, request.getMethod,
    //  s"EXIT-OK: setEndEventOffset(updated=${updateResult.getModifiedCount}, bpmn_id=$id, name=$name)", request)
  }

  private def getTimerDuration(ted: TimerEventDefinition, process: DynDoc, bpmnName: String,
      durations: Seq[(String, String, Int)], request: HttpServletRequest): Long = {
    val timerDurations = durations.filter(_._1 == "T").map(t => (t._2, t._3)).toMap
    val theTimer: DynDoc = process.timers[Many[Document]].filter(_.bpmn_name[String] == bpmnName).
      filter(_.bpmn_id[String] == ted.getParentElement.getAttributeValue("id")).head
    if (timerDurations.contains(theTimer.bpmn_id[String])) {
      duration2ms(theTimer.duration[String]) / 86400000L
    } else {
      theTimer.get[String]("duration") match {
        case Some(duration) => duration.substring(0, duration.indexOf(':')).toInt
        case None => 0
      }
    }
  }

  private def setCriticalPath(ut: FlowNode, process: DynDoc, bpmnName: String, request: HttpServletRequest): Unit = {
    //BWLogger.log(getClass.getName, request.getMethod, s"ENTRY: setCriticalPath()", request)
    val activityOids: Seq[ObjectId] = process.activity_ids[Many[ObjectId]]
    val name = ut.getAttributeValue("name")
    val id = ut.getAttributeValue("id")
    val query = Map("_id" -> Map("$in" -> activityOids), "bpmn_name" -> bpmnName, "bpmn_id" -> id, "name" -> name)
    val updateResult = BWMongoDB3.activities.updateOne(query, Map($set -> Map("on_critical_path" -> true)))
    if (updateResult.getMatchedCount == 0) {
      BWLogger.log(getClass.getName, request.getMethod, s"ERROR:setCriticalPath:query=$query", request)
      //throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    }
    //BWLogger.log(getClass.getName, request.getMethod,
    //  s"EXIT-OK: setCriticalPath(updated=${updateResult.getModifiedCount}, bpmn_id=$id, name=$name)", request)
  }

  private def getActivityDuration(bpmnId: String, process: DynDoc, bpmnName: String,
      durations: Seq[(String, String, Int)], request: HttpServletRequest): Long = {
    val activityOids: Seq[ObjectId] = process.activity_ids[Many[ObjectId]]
    val query = Map("_id" -> Map("$in" -> activityOids), "bpmn_name" -> bpmnName, "bpmn_id" -> bpmnId)
    BWMongoDB3.activities.find(query).headOption match {
      case Some(theActivity) =>
        val activityDurations = durations.filter(_._1 == "A").map(t => (new ObjectId(t._2), t._3)).toMap
        if (activityDurations.contains(theActivity._id[ObjectId])) {
          activityDurations(theActivity._id[ObjectId])
        } else {
          ActivityApi.durationLikely3(theActivity) match {
            case Some(days) => days
            case None => 0
          }
        }
      case None =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: getActivityDuration() cant find activity", request)
        0
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

      def maxPredecessorOffset(flowNode: FlowNode): Long = {
        val thePredecessors = predecessors(flowNode)
        if (thePredecessors.nonEmpty) {
          val nodesAndOffsets = thePredecessors.
              map(node => (node, getTimeOffset(node, bpmnName, onCriticalPath, seenNodes + flowNode)))
          val maxOffset = nodesAndOffsets.map(_._2).max
          if (maxOffset != 0) {
            nodesAndOffsets.find(node => node._2 == maxOffset && node._1.isInstanceOf[UserTask]) match {
              case Some(nodeAndOffset) => setCriticalPath(nodeAndOffset._1, process, bpmnName, request)
              case None => // do nothing
            }
          }
          maxOffset
        } else
          0
      }

      def minPredecessorOffset(flowNode: FlowNode) = predecessors(flowNode).
          map(n => getTimeOffset(n, bpmnName, onCriticalPath, seenNodes + flowNode)).min

      val timeOffset = node match {
        case userTask: UserTask =>
          maxPredecessorOffset(userTask) + getActivityDuration(userTask.getId, process, bpmnName, durations, request)
        case serviceTask: ServiceTask =>
          maxPredecessorOffset(serviceTask)
        case _: StartEvent =>
          0
        case parallelGateway: ParallelGateway =>
          maxPredecessorOffset(parallelGateway)
        case exclusiveGateway: ExclusiveGateway =>
          minPredecessorOffset(exclusiveGateway)
        case endEvent: EndEvent =>
          val endOffset = maxPredecessorOffset(endEvent)
          if (durations.nonEmpty)
            setEndEventOffset(endEvent, process, bpmnName, endOffset, request)
          endOffset
        case callActivity: CallActivity =>
          val calledElement = callActivity.getCalledElement
          val calledBpmnDuration = if (calledElement == "Infra-Activity-Handler") {
            getActivityDuration(callActivity.getId, process, bpmnName, durations, request)
          } else {
            val bpmnModel2 = bpmnModelInstance(calledElement)
            val endEvents: Seq[EndEvent] = bpmnModel2.getModelElementsByType(classOf[EndEvent]).asScala.toSeq
            getTimeOffset(endEvents.head, calledElement, onCriticalPath, seenNodes + callActivity)
          }
          maxPredecessorOffset(callActivity) + calledBpmnDuration
        case ite: IntermediateThrowEvent => // Milestone
          val milestoneOffset = maxPredecessorOffset(ite)
          if (durations.nonEmpty)
            setMilestoneOffset(ite, process, bpmnName, milestoneOffset, request)
          milestoneOffset
        case ice: IntermediateCatchEvent if !ice.getChildElementsByType(classOf[TimerEventDefinition]).isEmpty =>
          val ted = ice.getChildElementsByType(classOf[TimerEventDefinition]).asScala.head
          val delay = getTimerDuration(ted, process, bpmnName, durations, request)
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
      BWLogger.log(getClass.getName, request.getMethod,
         s"WARN: processDurationRecalculate() found ${endEvents.length} EndEvent nodes in BPMN", request)
    val offset = getTimeOffset(endEvents.head, bpmnName, onCriticalPath = true, Set.empty[FlowNode])
    offset
  }

}
