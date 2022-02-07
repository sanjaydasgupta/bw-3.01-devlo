package com.buildwhiz.baf3

import com.buildwhiz.baf2.{ActivityApi, PhaseApi, ProcessApi}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, BpmnUtils}
import com.mongodb.client.model.UpdateOneModel
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.model.bpmn.instance._

import javax.servlet.http.HttpServletRequest
import scala.jdk.CollectionConverters._
import scala.collection.mutable

object ProcessBpmnTraverse extends BpmnUtils {

  private def setMilestoneOffset(ted: IntermediateThrowEvent, process: DynDoc, bpmnName: String, offset: Long,
      request: HttpServletRequest): Unit = {
    //BWLogger.log(getClass.getName, request.getMethod, s"ENTRY: setMilestoneOffset()", request)
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
    //BWLogger.log(getClass.getName, request.getMethod, s"ENTRY: setEndEventOffset()", request)
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

  private def setCallActivitySchedule(process: DynDoc, bpmnName: String, offset: Long, duration: Long,
      request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getName, request.getMethod,
        s"setCallActivityDuration(bpmn_name: $bpmnName, duration: $duration)", request)
    val processOid = process._id[ObjectId]
    val query = Map("_id" -> processOid, "bpmn_timestamps" -> Map($elemMatch -> Map("name" -> bpmnName)))
    val setter = Map($set -> Map("bpmn_timestamps.$.offset_scheduled" -> offset,
        "bpmn_timestamps.$.duration_scheduled" -> duration))
    val updateResult = BWMongoDB3.processes.updateOne(query, setter)
    if (updateResult.getMatchedCount == 0) {
      BWLogger.log(getClass.getName, request.getMethod, s"ERROR: setEndEventOffset:query=$query, " +
        s"setter=$setter", request)
    }
  }

  private def getTimerDuration(ice: IntermediateCatchEvent, process: DynDoc, bpmnName: String,
      durations: Seq[(String, String, Int)], request: HttpServletRequest): Long = {
    val timerId = ice.getAttributeValue("id")
    //BWLogger.log(getClass.getName, request.getMethod,
    //  s"""getTimerDuration(timer-id:$timerId, durations:${durations.map(d => s"(${d._1},${d._2},${d._3})").mkString(", ")})""", request)
    val timerDurations = durations.filter(_._1 == "T").map(t => (t._2, t._3)).toMap
    process.timers[Many[Document]].find(p => p.bpmn_name[String] == bpmnName && p.bpmn_id[String] == timerId) match {
      case Some(theTimer) =>
        if (timerDurations.contains(theTimer.bpmn_id[String])) {
          //BWLogger.log(getClass.getName, request.getMethod, s"getTimerDuration(found: $timerId)", request)
          timerDurations(theTimer.bpmn_id[String])
        } else {
          //BWLogger.log(getClass.getName, request.getMethod, s"getTimerDuration(Not-Found1: $timerId)", request)
          theTimer.get[String]("duration") match {
            case Some(duration) => duration.substring(0, duration.indexOf(':')).toInt
            case None => 0
          }
        }
      case None =>
        //BWLogger.log(getClass.getName, request.getMethod, s"getTimerDuration(Not-Found2: $timerId)", request)
        val ted = ice.getChildElementsByType(classOf[TimerEventDefinition]).asScala.head
        val timer = s"ted: ${ted.getAttributeValue("name")}, process: ${process.name[String]}, bpmnName: $bpmnName"
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: getTimerDuration() cant find $timer", request)
        0
    }
  }

  private def setUserTaskOffset(ut: Task, process: DynDoc, bpmnNameFull: String, offset: Long,
      taktUnitCount: Int, firstTaktTaskDuration: Long, request: HttpServletRequest): Unit = {
    val id = ut.getAttributeValue("id")
    val inMessage = s"ENTRY: setUserTaskOffset(id=$id, bpmnNameFull=$bpmnNameFull, taktUnitCount=$taktUnitCount, offset=$offset)"
    BWLogger.log(getClass.getName, request.getMethod, inMessage, request)
    val activityOids = process.activity_ids[Many[ObjectId]]
    val bulkWriteBuffer = new java.util.ArrayList[UpdateOneModel[Document]]()
    for (taktUnitNo <- Seq.range(1, taktUnitCount + 1)) {
      val query = new Document("_id", new Document("$in", activityOids)).append("bpmn_name_full", bpmnNameFull).
      append("bpmn_id", id).append("takt_unit_no", taktUnitNo)
      val taktOffset = offset + (taktUnitNo - 1) * firstTaktTaskDuration
      bulkWriteBuffer.add(new UpdateOneModel(query, new Document($set, new Document("offset", taktOffset))))
    }
    val bulkWriteResult = BWMongoDB3.activities.bulkWrite(bulkWriteBuffer)
    if (bulkWriteResult.getMatchedCount == 0) {
      BWLogger.log(getClass.getName, request.getMethod, s"ERROR:setUserTaskOffset:no-matches", request)
      throw new IllegalArgumentException(s"MongoDB update failed: $bulkWriteResult")
    }
    val outMessage = s"ENTRY: setUserTaskOffset(id=$id, bpmnNameFull=$bpmnNameFull, taktUnitCount=$taktUnitCount, offset=$offset)"
    BWLogger.log(getClass.getName, request.getMethod, outMessage, request)
  }

  private def getActivityDuration(bpmnId: String, process: DynDoc, bpmnNameFull: String,
      durations: Seq[(String, String, Int)], activitiesByBpmnNameFullIdAndTaktUnitNo: Map[(String, String, Int), DynDoc],
      buffer: mutable.Buffer[String]): Long = {
      activitiesByBpmnNameFullIdAndTaktUnitNo.get((bpmnNameFull, bpmnId, 1)) match {
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
        val message = s"id: $bpmnId, process: ${process.name[String]}, bpmnNameFull: $bpmnNameFull"
        buffer.append(message)
        0
    }
  }

  def processDurationRecalculate(bpmnName: String, phaseOid: ObjectId, bpmnNameFull: String,
      durations: Seq[(String, String, Int)], request: HttpServletRequest): Long = {

    val process = PhaseApi.allProcesses(phaseOid) match {
      case Seq(soloProcess) => soloProcess
      case _ => throw new IllegalArgumentException("Bad phase: mis-configured processes")
    }

    val duration = if (durations.isEmpty) {
      processDurationRecalculate(bpmnName, process, 0, bpmnNameFull, Seq.empty, request)
    } else {
      if (bpmnName != process.bpmn_name[String]) {
        processDurationRecalculate(process.bpmn_name[String], process, 0, process.bpmn_name[String], durations,
            request)
        processDurationRecalculate(bpmnName, process, 0, bpmnNameFull, Seq.empty, request)
      } else {
        processDurationRecalculate(bpmnName, process, 0, bpmnNameFull, durations, request)
      }
    }
    duration
  }

  def processDurationRecalculate(bpmnName: String, process: DynDoc, processOffset: Long, bpmnNameFull: String,
      durations: Seq[(String, String, Int)], request: HttpServletRequest): Long = {
    val t0 = System.currentTimeMillis()
    val isTakt = process.bpmn_timestamps[Many[Document]].
      exists(ts => ts.name[String] == bpmnName && ts.get[Boolean]("is_takt").contains(true))
    val bpmnModel = bpmnModelInstance(bpmnName)
    val allActivities = ProcessApi.allActivities(Right(process), Map("bpmn_name_full" -> bpmnNameFull))
    val activitiesByBpmnNameIdAndTakt: Map[(String, String, Int), DynDoc] =
      allActivities.map(a => ((a.bpmn_name_full[String], a.bpmn_id[String], a.takt_unit_no[Int]), a)).toMap
    val baseActivities = allActivities.filter(_.takt_unit_no[Int] == 1)
    val messages = mutable.ListBuffer[String]()
    val (taktUnitCount: Int, firstTaktTaskDuration: Long) = if (isTakt) {
      val tuc = {
        if (allActivities.length == baseActivities.length) {
          val taktActivityCount = bpmnModel.getModelElementsByType(classOf[Task]).asScala.size
          val phaseOid = ProcessApi.parentPhase(process._id)._id[ObjectId]
          math.max(PhaseApi.getTaktUnitCount(phaseOid, bpmnNameFull, taktActivityCount), 1)
        } else {
          math.max(allActivities.length / baseActivities.length, 1)
        }
      }
      val fttd = {
        val startEvent = bpmnModel.getModelElementsByType(classOf[StartEvent]).asScala.head
        val firstTask = startEvent.getSucceedingNodes.list().asScala.head
        getActivityDuration(firstTask.getId, process, bpmnNameFull, durations, activitiesByBpmnNameIdAndTakt,
            messages)
      }
      (tuc, fttd)
    } else {
      (1, 0L)
    }
    val offsetsCache = mutable.Map.empty[FlowNode, Long]

    def getTimeOffset(node: FlowNode): Long = {

      def maxPredecessorOffset(flowNode: FlowNode): Long = {
        if (offsetsCache.contains(flowNode)) {
          offsetsCache(flowNode)
        } else {
          val thePredecessors: Seq[FlowNode] = flowNode.getPreviousNodes.list().asScala.toSeq
          val offset = if (thePredecessors.nonEmpty) {
            thePredecessors.map(getTimeOffset).max
          } else {
            processOffset
          }
          offsetsCache(flowNode) = offset
          offset
        }
      }

      val timeOffset = node match {
        case serviceTask: ServiceTask =>
          maxPredecessorOffset(serviceTask)
        case aTask: Task =>
          val offset = maxPredecessorOffset(aTask)
          if (durations.nonEmpty)
            setUserTaskOffset(aTask, process, bpmnNameFull, offset, taktUnitCount, firstTaktTaskDuration, request)
          offset + getActivityDuration(aTask.getId, process, bpmnNameFull, durations, activitiesByBpmnNameIdAndTakt,
              messages)
        case _: StartEvent =>
          processOffset
        case parallelGateway: ParallelGateway =>
          maxPredecessorOffset(parallelGateway)
        case exclusiveGateway: ExclusiveGateway =>
          throw new IllegalArgumentException(s"Bad node (ExclusiveGateway): $exclusiveGateway")
        case endEvent: EndEvent =>
          val endOffset = maxPredecessorOffset(endEvent)
          if (durations.nonEmpty)
            setEndEventOffset(endEvent, process, bpmnName, endOffset, request)
          endOffset
        case callActivity: CallActivity =>
          val calledElement = callActivity.getCalledElement
          val callActivityId = callActivity.getId
          if (calledElement == "Infra-Activity-Handler") {
            getActivityDuration(callActivityId, process, bpmnNameFull, durations, activitiesByBpmnNameIdAndTakt,
                messages)
          } else {
            val callOffset = maxPredecessorOffset(callActivity)
            val bpmnNameFull2 = (bpmnNameFull.split("/").init ++ Seq(callActivityId, calledElement)).mkString("/")
            val duration = processDurationRecalculate(calledElement, process, callOffset, bpmnNameFull2, durations,
                request)
            if (durations.nonEmpty) {
              setCallActivitySchedule(process, calledElement, callOffset, duration, request)
            }
            callOffset + duration
          }
        case ite: IntermediateThrowEvent => // Milestone
          val milestoneOffset = maxPredecessorOffset(ite)
          if (durations.nonEmpty)
            setMilestoneOffset(ite, process, bpmnName, milestoneOffset, request)
          milestoneOffset
        case ice: IntermediateCatchEvent if !ice.getChildElementsByType(classOf[TimerEventDefinition]).isEmpty =>
          val offset = maxPredecessorOffset(ice)
          val delay = getTimerDuration(ice, process, bpmnName, durations, request)
          offset + delay
        case anyOtherNode: FlowNode =>
          maxPredecessorOffset(anyOtherNode)
      }
      timeOffset
    }

    val endEvents: Seq[EndEvent] = bpmnModel.getModelElementsByType(classOf[EndEvent]).asScala.toSeq
    val maxEndOffset = endEvents.map(getTimeOffset).max
    if (messages.nonEmpty) {
      BWLogger.log(getClass.getName, request.getMethod, s"""ERROR: ${messages.length} tasks not found""", request)
    }
    val unitDuration = maxEndOffset - processOffset
    if (isTakt) {
      val fullTaktDuration = unitDuration + firstTaktTaskDuration * math.max(0, taktUnitCount - 1)
      val delay = System.currentTimeMillis() - t0
      val message = s"processDurationRecalculate(bpmnNameFull: $bpmnNameFull) time: $delay ms"
      BWLogger.log(getClass.getName, request.getMethod, message, request)
      fullTaktDuration
    } else {
      val delay = System.currentTimeMillis() - t0
      val message = s"processDurationRecalculate(bpmnNameFull: $bpmnNameFull) time: $delay ms"
      BWLogger.log(getClass.getName, request.getMethod, message, request)
      unitDuration
    }
  }

}
