package com.buildwhiz.baf3

import com.buildwhiz.baf.PhaseBpmnTraverse.ms2duration
import com.buildwhiz.baf2.ActivityApi
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils._
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.model.bpmn.BpmnModelInstance
import org.camunda.bpm.model.bpmn.instance._

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.collection.JavaConverters._

class ProcessBpmnTraverse extends HttpServlet with HttpUtils with BpmnUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    doGetTransaction(request, response)
  }

  private def doGetTransaction(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val bpmnFileName = parameters("bpmn_name").replaceAll(" ", "-")
      val bpmnModel = bpmnModelInstance(bpmnFileName)
      val phaseOid = new ObjectId(parameters("phase_id"))
      val phase: DynDoc = BWMongoDB3.processes.find(Map("_id" -> phaseOid)).head
      val offset = ProcessBpmnTraverse.scheduleBpmnElements(bpmnModel, phase, bpmnFileName, request)
      response.getWriter.println(bson2json(new Document("min", ms2duration(offset._1)).
        append("max", ms2duration(offset._2))))
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
}

object ProcessBpmnTraverse extends HttpUtils with DateTimeUtils with ProjectUtils with BpmnUtils {

  private def getTimerDuration(ted: TimerEventDefinition, phase: DynDoc, bpmnName: String): Long = {
    val theTimer: DynDoc = phase.timers[Many[Document]].filter(_.bpmn_name[String] == bpmnName).
      filter(_.bpmn_id[String] == ted.getParentElement.getAttributeValue("id")).head
    duration2ms(theTimer.duration[String])
  }

  private def setTimerSchedule(ted: TimerEventDefinition, phase: DynDoc, bpmnName: String, entryOffset: (Long, Long),
      delay: Long, onCriticalPath: Boolean): Unit = {
    val timers: Seq[DynDoc] = phase.timers[Many[Document]]
    val idx = timers.indexWhere(t => t.bpmn_name[String] == bpmnName &&
      t.bpmn_id[String] == ted.getParentElement.getAttributeValue("id"))
    val averageOffset = (entryOffset._1 + entryOffset._2) / 2
    val (start, end) = (ms2duration(averageOffset), ms2duration(averageOffset + delay))
    val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> phase._id[ObjectId]),
      Map("$set" -> Map(s"timers.$idx.offset" -> Map("min" -> entryOffset._1, "max" -> entryOffset._2),
      s"timers.$idx.start" -> start, s"timers.$idx.end" -> end, s"timers.$idx.on_critical_path" -> onCriticalPath)))
    if (updateResult.getMatchedCount == 0)
      throw new IllegalArgumentException(s"MongoDB error: $updateResult")
  }

  private def getActivityDuration(bpmnId: String, phase: DynDoc, bpmnName: String): Long = {
    val activityOids: Seq[ObjectId] = phase.activity_ids[Many[ObjectId]]
    val theActivity: DynDoc = BWMongoDB3.activities.
      find(Map("_id" -> Map("$in" -> activityOids), "bpmn_name" -> bpmnName, "bpmn_id" -> bpmnId)).head
    ActivityApi.durationLikely3(theActivity) match {
      case Some(days) => days
      case None => 0
    }
  }

  private def setActionsSchedule(activity: DynDoc, entryOffset: (Long, Long), onCriticalPath: Boolean): Unit = {
    val actions: Seq[DynDoc] = activity.actions[Many[Document]]
    val prerequisites = actions.filter(_.`type`[String] == "prerequisite")
    val prerequisiteStart = ms2duration((entryOffset._1 + entryOffset._2) / 2)
    for (pr <- prerequisites) {
      pr.start = prerequisiteStart
      pr.end = ms2duration(duration2ms(prerequisiteStart) + duration2ms(pr.duration[String]))
      pr.on_critical_path = false
    }
    val main = actions.find(_.`type`[String] == "main").get
    main.start = if (prerequisites.isEmpty) prerequisiteStart else prerequisites.map(_.end[String]).max
    main.end = ms2duration(duration2ms(main.start[String]) + duration2ms(main.duration[String]))
    main.on_critical_path = onCriticalPath
    val reviews = actions.filter(_.`type`[String] == "review")
    val revStart = main.end[String]
    for (rev <- reviews) {
      rev.start = revStart
      rev.end = ms2duration(duration2ms(revStart) + duration2ms(rev.duration[String]))
      rev.on_critical_path = false
    }
    if (onCriticalPath) {
      def bigger(a: DynDoc, b: DynDoc): DynDoc =
          if (duration2ms(a.end[String]) > duration2ms(b.end[String])) a else b
      if (prerequisites.length > 1)
        prerequisites.reduce(bigger).on_critical_path = true
      else if (prerequisites.nonEmpty)
        prerequisites.head.on_critical_path = true
      if (reviews.length > 1)
        reviews.reduce(bigger).on_critical_path = true
      else if (reviews.nonEmpty)
        reviews.head.on_critical_path = true
    }
    val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activity._id[ObjectId]),
      Map("$set" -> Map("actions" -> actions.map(_.asDoc))))
    if (updateResult.getMatchedCount == 0)
      throw new IllegalArgumentException(s"MongoDB error: $updateResult")
  }

  private def setActivitySchedule(bpmnId: String, phase: DynDoc, bpmnName: String, entryOffset: (Long, Long),
      duration: Long, onCriticalPath: Boolean): Unit = {
    val activityOids: Seq[ObjectId] = phase.activity_ids[Many[ObjectId]]
    val theActivity: DynDoc = BWMongoDB3.activities.
      find(Map("_id" -> Map("$in" -> activityOids), "bpmn_name" -> bpmnName, "bpmn_id" -> bpmnId)).head
    //setActionsSchedule(theActivity, entryOffset, onCriticalPath)
    val averageOffset = (entryOffset._1 + entryOffset._2) / 2
    val (start, end) = (ms2duration(averageOffset), ms2duration(averageOffset + duration))
    val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> theActivity._id[ObjectId]),
      Map("$set" -> Map("offset" -> Map("min" -> entryOffset._1, "max" -> entryOffset._2), "start" -> start,
        "end" -> end, "duration" -> ms2duration(duration), "on_critical_path" -> onCriticalPath)))
    if (updateResult.getMatchedCount == 0)
      throw new IllegalArgumentException(s"MongoDB error: $updateResult")
  }

  private def setSubProcessSchedule(phase: DynDoc, calledElement: String, bpmnName: String, entryOffset: (Long, Long),
      exitOffset: (Long, Long), onCriticalPath: Boolean): Unit = {
    val start = ms2duration((entryOffset._1 + entryOffset._2) / 2)
    val end = ms2duration((exitOffset._1 + exitOffset._2) / 2)
    val bpmnTimestamps: Seq[DynDoc] = phase.bpmn_timestamps[Many[Document]]
    val idx = bpmnTimestamps.indexWhere(ts => ts.name[String] == calledElement && ts.parent_name[String] == bpmnName)
    val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> phase._id[ObjectId]), Map("$set" ->
      Map(s"bpmn_timestamps.$idx.offset" -> Map("start" -> start, "end" -> end),
        s"bpmn_timestamps.$idx.on_critical_path" -> onCriticalPath)))
    if (updateResult.getMatchedCount == 0)
      throw new IllegalArgumentException(s"MongoDB error: $updateResult")
  }

  def processDuration(bpmnName: String, phaseOid: ObjectId, request: HttpServletRequest): (Long, Long) = {
    val bpmnModel = bpmnModelInstance(bpmnName)
    val phase: DynDoc = BWMongoDB3.processes.find(Map("_id" -> phaseOid)).head
    scheduleBpmnElements(bpmnModel, phase, bpmnName, request)
  }

  private def scheduleBpmnElements(topLevelBpmnModel: BpmnModelInstance, phase: DynDoc, topLevelBpmnName: String,
      request: HttpServletRequest): (Long, Long) = {

    def getTimeOffset(node: FlowNode, processOffset: (Long, Long), bpmnName: String, onCriticalPath: Boolean,
        seenNodes: Set[FlowNode]): (Long, Long) = {
      //BWLogger.log(getClass.getName, "getTimeOffset", s"ENTRY(${node.getName})", request)

      def predecessors(node: FlowNode): Seq[FlowNode] = {
        val incomingFlows: Seq[SequenceFlow] = node.getIncoming.asScala.toSeq
        val allPredecessors = incomingFlows.map(f => if (f.getTarget == node) f.getSource else f.getTarget)
        val unseenPredecessors = allPredecessors.diff(seenNodes.toSeq)
        unseenPredecessors
      }

      def minMin(offsets: Seq[(Long, Long)]): (Long, Long) = if (offsets.isEmpty)
        (0L, 0L)
      else
        (offsets.map(_._1).min, offsets.map(_._2).min)
      //def minMax(offsets: Seq[(Long, Long)]) = (offsets.map(_._1).min, offsets.map(_._2).max)
      def maxMax(offsets: Seq[(Long, Long)]): (Long, Long) = if (offsets.isEmpty)
        (0L, 0L)
      else
        (offsets.map(_._1).max, offsets.map(_._2).max)

      node match {
        case userTask: UserTask =>
          val entryOffset = maxMax(predecessors(userTask).
            map(n => getTimeOffset(n, processOffset, bpmnName, onCriticalPath, seenNodes + userTask)))
          val duration = getActivityDuration(userTask.getId, phase, bpmnName)
          //setActivitySchedule(userTask.getId, phase, bpmnName, entryOffset, duration, onCriticalPath)
          maxMax(predecessors(userTask).
          map(n => getTimeOffset(n, processOffset, bpmnName, onCriticalPath, seenNodes + userTask)))
        case serviceTask: ServiceTask => maxMax(predecessors(serviceTask).
            map(n => getTimeOffset(n, processOffset, bpmnName, onCriticalPath, seenNodes + serviceTask)))
        case _: StartEvent =>
          processOffset
        case parallelGateway: ParallelGateway =>
          val predecessorNodes = predecessors(parallelGateway)
          val offsets = predecessorNodes.map(n => getTimeOffset(n, processOffset, bpmnName, onCriticalPath = false,
              seenNodes + parallelGateway))
          if (onCriticalPath) {
            val criticalPath = if (predecessorNodes.length == 1)
              predecessorNodes.head
            else
              predecessorNodes.zip(offsets).reduce((a, b) => if (a._2._2 > b._2._2) a else b)._1
            getTimeOffset(criticalPath, processOffset, bpmnName, onCriticalPath, seenNodes + parallelGateway)
          }
          maxMax(offsets)
        case exclusiveGateway: ExclusiveGateway =>
          minMin(predecessors(exclusiveGateway).map(n => getTimeOffset(n, processOffset, bpmnName, onCriticalPath,
              seenNodes + exclusiveGateway)))
        case endEvent: EndEvent =>
          val exitOffset = maxMax(predecessors(endEvent).
              map(n => getTimeOffset(n, processOffset, bpmnName, onCriticalPath, seenNodes + endEvent)))
          exitOffset
        case callActivity: CallActivity =>
          val entryOffset = maxMax(predecessors(callActivity).
              map(n => getTimeOffset(n, processOffset, bpmnName, onCriticalPath, seenNodes + callActivity)))
          if (callActivity.getCalledElement == "Infra-Activity-Handler") {
            val duration = getActivityDuration(callActivity.getId, phase, bpmnName)
            //setActivitySchedule(callActivity.getId, phase, bpmnName, entryOffset, duration, onCriticalPath)
            val exitOffset = (entryOffset._1 + duration, entryOffset._2 + duration)
            exitOffset
          } else {
            val calledElement = callActivity.getCalledElement
            val bpmnModel2 = bpmnModelInstance(calledElement)
            val endEvents: Seq[EndEvent] = bpmnModel2.getModelElementsByType(classOf[EndEvent]).asScala.toSeq
            val exitOffset = getTimeOffset(endEvents.head, entryOffset, calledElement, onCriticalPath,
                seenNodes + callActivity)
            //setSubProcessSchedule(phase, calledElement, bpmnName, entryOffset, exitOffset, onCriticalPath)
            exitOffset
          }
        case ice: IntermediateCatchEvent if !ice.getChildElementsByType(classOf[TimerEventDefinition]).isEmpty =>
          val entryOffset = maxMax(predecessors(ice).
              map(n => getTimeOffset(n, processOffset, bpmnName, onCriticalPath, seenNodes + ice)))
          val ted = ice.getChildElementsByType(classOf[TimerEventDefinition]).asScala.head
          val delay = getTimerDuration(ted, phase, bpmnName)
          //setTimerSchedule(ted, phase, bpmnName, entryOffset, delay, onCriticalPath)
          val exitOffset = (entryOffset._1 + delay, entryOffset._2 + delay)
          exitOffset
        case anyOtherType =>
          val entryOffset = maxMax(predecessors(anyOtherType).
            map(n => getTimeOffset(n, processOffset, bpmnName, onCriticalPath, seenNodes + anyOtherType)))
          entryOffset
      }
    }

    val endEvents: Seq[EndEvent] = topLevelBpmnModel.getModelElementsByType(classOf[EndEvent]).asScala.toSeq
    val offset = getTimeOffset(endEvents.head, (0, 0), topLevelBpmnName, onCriticalPath = true, Set.empty[FlowNode])
    val end = ms2duration(duration2ms(phase.start[String]) + (offset._1 + offset._2) / 2)
    val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> phase._id[ObjectId]),
      Map("$set" -> Map("end" -> end)))
    if (updateResult.getMatchedCount == 0)
      throw new IllegalArgumentException(s"MongoDB error: $updateResult")
    offset
  }

}
