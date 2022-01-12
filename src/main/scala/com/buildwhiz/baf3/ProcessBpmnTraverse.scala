package com.buildwhiz.baf3

import com.buildwhiz.baf2.{ActivityApi, PhaseApi, ProcessApi}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BpmnUtils, BWLogger, DateTimeUtils, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.model.bpmn.instance._

import javax.servlet.http.HttpServletRequest
import scala.collection.JavaConverters._
import scala.collection.mutable

object ProcessBpmnTraverse extends HttpUtils with DateTimeUtils with BpmnUtils {

  private def setMilestoneOffset(ted: IntermediateThrowEvent, process: DynDoc, bpmnName: String, offset: Long,
      request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY: setMilestoneOffset()", request)
    val processOid = process._id[ObjectId]
    //val name = ted.getAttributeValue("name")
    val id = ted.getAttributeValue("id")
    val query = Map("_id" -> processOid, "milestones" -> Map($elemMatch -> Map("bpmn_id" -> id, "bpmn_name" -> bpmnName)))
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
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY: setEndEventOffset()", request)
    val processOid = process._id[ObjectId]
    //val name = endEvent.getAttributeValue("name")
    val id = endEvent.getAttributeValue("id")
    val query = Map("_id" -> processOid, "end_nodes" -> Map($elemMatch -> Map("bpmn_id" -> id, "bpmn_name" -> bpmnName)))
    val setter = Map($set -> Map("end_nodes.$.offset" -> offset))
    val updateResult = BWMongoDB3.processes.updateOne(query, setter)
    if (updateResult.getMatchedCount == 0) {
      BWLogger.log(getClass.getName, request.getMethod,
        s"ERROR: setEndEventOffset:query=$query, setter=$setter", request)
      //throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    }
    //BWLogger.log(getClass.getName, request.getMethod,
    //  s"EXIT-OK: setEndEventOffset(updated=${updateResult.getModifiedCount}, bpmn_id=$id, name=$name)", request)
  }

  private def getTimerDuration(ice: IntermediateCatchEvent, process: DynDoc, bpmnName: String,
      durations: Seq[(String, String, Int)], request: HttpServletRequest): Long = {
    val timerId = ice.getAttributeValue("id")
    BWLogger.log(getClass.getName, request.getMethod,
      s"""getTimerDuration(timer-id:$timerId, durations:${durations.map(d => s"(${d._1},${d._2},${d._3})").mkString(", ")})""", request)
    val timerDurations = durations.filter(_._1 == "T").map(t => (t._2, t._3)).toMap
    process.timers[Many[Document]].find(p => p.bpmn_name[String] == bpmnName && p.bpmn_id[String] == timerId) match {
      case Some(theTimer) =>
        if (timerDurations.contains(theTimer.bpmn_id[String])) {
          BWLogger.log(getClass.getName, request.getMethod, s"getTimerDuration(found: $timerId)", request)
          timerDurations(theTimer.bpmn_id[String])
        } else {
          BWLogger.log(getClass.getName, request.getMethod, s"getTimerDuration(Not-Found1: $timerId)", request)
          theTimer.get[String]("duration") match {
            case Some(duration) => duration.substring(0, duration.indexOf(':')).toInt
            case None => 0
          }
        }
      case None =>
        BWLogger.log(getClass.getName, request.getMethod, s"getTimerDuration(Not-Found2: $timerId)", request)
        val ted = ice.getChildElementsByType(classOf[TimerEventDefinition]).asScala.head
        val timer = s"ted: ${ted.getAttributeValue("name")}, process: ${process.name[String]}, bpmnName: $bpmnName"
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: getTimerDuration() cant find $timer", request)
        0
    }
  }

  private def setUserTaskOffset(ut: Task, process: DynDoc, bpmnName: String, offset: Long,
      request: HttpServletRequest): Unit = {
    val id = ut.getAttributeValue("id")
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY: setUserTaskOffset(id=$id, bpmnName=$bpmnName, offset=$offset)", request)
    val activityOids: Seq[ObjectId] = process.activity_ids[Many[ObjectId]]
    //val name = ut.getAttributeValue("name")
    val query = Map("_id" -> Map("$in" -> activityOids), "bpmn_name" -> bpmnName, "bpmn_id" -> id)
    val updateResult = BWMongoDB3.activities.updateOne(query, Map($set -> Map("offset" -> offset)))
    if (updateResult.getMatchedCount == 0) {
      BWLogger.log(getClass.getName, request.getMethod, s"ERROR:setUserTaskOffset:query=$query", request)
      //throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    }
    //BWLogger.log(getClass.getName, request.getMethod,
    //  s"EXIT-OK: setCriticalPath(updated=${updateResult.getModifiedCount}, bpmn_id=$id, name=$name)", request)
  }

  private def getActivityDuration(bpmnId: String, process: DynDoc, bpmnName: String,
      durations: Seq[(String, String, Int)], activitiesByBpmnNameAndId: Map[(String, String), DynDoc],
      buffer: mutable.Buffer[String], request: HttpServletRequest): Long = {
    activitiesByBpmnNameAndId.get((bpmnName, bpmnId)) match {
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
        val message = s"id: $bpmnId, process: ${process.name[String]}, bpmnName: $bpmnName"
        buffer.append(message)
        0
    }
  }

  def processDurationRecalculate(bpmnName: String, phaseOid: ObjectId, bpmnNameFull: String,
      durations: Seq[(String, String, Int)], repetitionCount: Int, request: HttpServletRequest): Long = {

    val process = PhaseApi.allProcesses(phaseOid) match {
      case Seq(soloProcess) => soloProcess
      case _ => throw new IllegalArgumentException("Bad phase: mis-configured processes")
    }

    val duration = if (durations.isEmpty) {
      processDurationRecalculate(bpmnName, process, 0, bpmnNameFull, Seq.empty, repetitionCount, request)
    } else {
      if (bpmnName != process.bpmn_name[String]) {
        processDurationRecalculate(process.bpmn_name[String], process, 0, process.bpmn_name[String], durations,
            repetitionCount, request)
        processDurationRecalculate(bpmnName, process, 0, bpmnNameFull, Seq.empty, repetitionCount, request)
      } else {
        processDurationRecalculate(bpmnName, process, 0, bpmnNameFull, durations, repetitionCount, request)
      }
    }
    duration
  }

  def processDurationRecalculate(bpmnName: String, process: DynDoc, processOffset: Long, bpmnNameFull: String,
      durations: Seq[(String, String, Int)], repetitionCount: Int, request: HttpServletRequest): Long = {
    val t0 = System.currentTimeMillis()
    val activities = ProcessApi.allActivities(process)
    val activitiesByBpmnNameAndId: Map[(String, String), DynDoc] =
        activities.map(a => ((a.bpmn_name[String], a.bpmn_id[String]), a)).toMap
    val messages = mutable.ListBuffer[String]()

    def getFirstTaskDuration(startNode: FlowNode, bpmnName: String): Long = {
      val firstTask = startNode.getSucceedingNodes.list().asScala.head
      getActivityDuration(firstTask.getId, process, bpmnName, Seq.empty, activitiesByBpmnNameAndId,
        messages, request)
    }

    def getTimeOffset(node: FlowNode, startOffset: Long, bpmnName: String, prefix: String/*, seenNodes: Set[FlowNode]*/): Long = {

      def predecessors(flowNode: FlowNode): Seq[FlowNode] = {
        val previousNodes: Seq[FlowNode] = flowNode.getPreviousNodes.list().asScala
        val unseenPredecessors = previousNodes//.diff(seenNodes.toSeq)
        unseenPredecessors
      }

      def maxPredecessorOffset(flowNode: FlowNode, stOffset: Long): Long = {
        val thePredecessors = predecessors(flowNode)
        if (thePredecessors.nonEmpty) {
          thePredecessors.map(predNode => getTimeOffset(predNode, stOffset, bpmnName, prefix/*, seenNodes + predNode*/)).max
        } else
          stOffset
      }

//      def minPredecessorOffset(flowNode: FlowNode, stOffset: Long) = predecessors(flowNode).
//          map(n => getTimeOffset(n, stOffset, bpmnName, prefix/*, seenNodes + n*/)).min
//
      //BWLogger.log(getClass.getName, request.getMethod, s"ENTRY getTimeOffset(bpmnName=$bpmnName, startOffset=$startOffset)", request)
      val timeOffset = node match {
        case serviceTask: ServiceTask =>
          maxPredecessorOffset(serviceTask, startOffset)
        case aTask: Task =>
          val offset = maxPredecessorOffset(aTask, startOffset)
          if (durations.nonEmpty)
            setUserTaskOffset(aTask, process, bpmnName, offset, request)
          offset + getActivityDuration(aTask.getId, process, bpmnName, durations, activitiesByBpmnNameAndId,
              messages, request)
        case _: StartEvent =>
          startOffset
        case parallelGateway: ParallelGateway =>
          maxPredecessorOffset(parallelGateway, startOffset)
//        case exclusiveGateway: ExclusiveGateway =>
//          minPredecessorOffset(exclusiveGateway, startOffset)
        case endEvent: EndEvent =>
          val endOffset = maxPredecessorOffset(endEvent, startOffset)
          if (durations.nonEmpty)
            setEndEventOffset(endEvent, process, bpmnName, endOffset, request)
          endOffset
        case callActivity: CallActivity =>
          val calledElement = callActivity.getCalledElement
          val callActivityId = callActivity.getId
          val calledBpmnDuration = if (calledElement == "Infra-Activity-Handler") {
            getActivityDuration(callActivityId, process, bpmnName, durations, activitiesByBpmnNameAndId,
                messages, request)
          } else {
            val callOffset = maxPredecessorOffset(callActivity, startOffset)
            val bpmnNameFull2 = (bpmnNameFull.split("/").init ++ Seq(callActivityId, calledElement)).mkString("/")
            val isTakt = callActivity.getLoopCharacteristics != null
            if (isTakt) {
              val bpmnModel2 = bpmnModelInstance(calledElement)
              val phaseOid: ObjectId = ProcessApi.parentPhase(process._id)._id
              val activityCount = bpmnModel2.getModelElementsByType(classOf[Task]).asScala.size
              val repetitionCount2: Int = PhaseApi.getTaktUnitCount(phaseOid, bpmnNameFull2, activityCount)
              val duration = processDurationRecalculate(calledElement, process, callOffset, bpmnNameFull2, durations,
                  repetitionCount2, request)
              val startEvent: StartEvent = bpmnModel2.getModelElementsByType(classOf[StartEvent]).asScala.head
              callOffset + duration + getFirstTaskDuration(startEvent, calledElement) * repetitionCount
            } else {
              val duration = processDurationRecalculate(calledElement, process, callOffset, bpmnNameFull2, durations,
                  repetitionCount, request)
              callOffset + duration
            }
          }
          /*maxPredecessorOffset(callActivity, startOffset) + */calledBpmnDuration
        case ite: IntermediateThrowEvent => // Milestone
          val milestoneOffset = maxPredecessorOffset(ite, startOffset)
          if (durations.nonEmpty)
            setMilestoneOffset(ite, process, bpmnName, milestoneOffset, request)
          milestoneOffset
        case ice: IntermediateCatchEvent if !ice.getChildElementsByType(classOf[TimerEventDefinition]).isEmpty =>
          val offset = maxPredecessorOffset(ice, startOffset)
          val delay = getTimerDuration(ice, process, bpmnName, durations, request)
          offset + delay
        case anyOtherNode: FlowNode =>
          maxPredecessorOffset(anyOtherNode, startOffset)
      }
      timeOffset
    }

    val bpmnModel = bpmnModelInstance(bpmnName)
    val domPrefix = bpmnModel.getDocument.getRootElement.getPrefix
    val endEvents: Seq[EndEvent] = bpmnModel.getModelElementsByType(classOf[EndEvent]).asScala.toSeq
    val offset = endEvents.map(ee => getTimeOffset(ee, processOffset, bpmnName, domPrefix/*, Set.empty[FlowNode]*/)).max
    if (messages.nonEmpty) {
      BWLogger.log(getClass.getName, request.getMethod, s"""ERROR: ${messages.length} tasks not found""", request)
    }
    val delay = System.currentTimeMillis() - t0
    BWLogger.log(getClass.getName, request.getMethod, s"processDurationRecalculate() time: $delay ms", request)
    offset
  }

}
