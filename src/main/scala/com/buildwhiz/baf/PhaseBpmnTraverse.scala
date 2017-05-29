package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils._
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.model.bpmn.BpmnModelInstance
import org.camunda.bpm.model.bpmn.instance._

import scala.collection.JavaConverters._

class PhaseBpmnTraverse extends HttpServlet with HttpUtils with BpmnUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val bpmnFileName = parameters("bpmn_name").replaceAll(" ", "-")
      val bpmnModel = bpmnModelInstance(bpmnFileName)
      val phaseOid = new ObjectId(parameters("phase_id"))
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      PhaseBpmnTraverse.scheduleBpmnElements(bpmnModel, phase, bpmnFileName, request, response)
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

object PhaseBpmnTraverse extends HttpUtils with DateTimeUtils with ProjectUtils with BpmnUtils {

  private def getTimerDuration(ted: TimerEventDefinition, phase: DynDoc, bpmnName: String): Long = {
    val theTimer: DynDoc = phase.timers[Many[Document]].filter(_.bpmn_name[String] == bpmnName).
      filter(_.bpmn_id[String] == ted.getParentElement.getAttributeValue("id")).head
    duration2ms(theTimer.duration[String])
  }

  private def setTimerSchedule(ted: TimerEventDefinition, phase: DynDoc, bpmnName: String, entryOffset: (Long, Long),
      delay: Long): Unit = {
    val timers: Seq[DynDoc] = phase.timers[Many[Document]]
    val idx = timers.indexWhere(t => t.bpmn_name[String] == bpmnName &&
      t.bpmn_id[String] == ted.getParentElement.getAttributeValue("id"))
    val averageOffset = (entryOffset._1 + entryOffset._2) / 2
    val (start, end) = (ms2duration(averageOffset), ms2duration(averageOffset + delay))
    val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phase._id[ObjectId]),
      Map("$set" -> Map(s"timers.$idx.offset" -> Map("min" -> entryOffset._1, "max" -> entryOffset._2),
      s"timers.$idx.start" -> start, s"timers.$idx.end" -> end)))
    if (updateResult.getMatchedCount == 0)
      throw new IllegalArgumentException(s"MongoDB error: $updateResult")
  }

  private def getActivityDuration(bpmnId: String, phase: DynDoc, bpmnName: String): Long = {
    val activityOids: Seq[ObjectId] = phase.activity_ids[Many[ObjectId]]
    val theActivity: DynDoc = BWMongoDB3.activities.
      find(Map("_id" -> Map("$in" -> activityOids), "bpmn_name" -> bpmnName, "bpmn_id" -> bpmnId)).head
    duration2ms(getActivityDuration(theActivity))
  }

  private def setActivitySchedule(bpmnId: String, phase: DynDoc, bpmnName: String, entryOffset: (Long, Long),
      delay: Long): Unit = {
    val activityOids: Seq[ObjectId] = phase.activity_ids[Many[ObjectId]]
    val theActivity: DynDoc = BWMongoDB3.activities.
      find(Map("_id" -> Map("$in" -> activityOids), "bpmn_name" -> bpmnName, "bpmn_id" -> bpmnId)).head
    val averageOffset = (entryOffset._1 + entryOffset._2) / 2
    val (start, end) = (ms2duration(averageOffset), ms2duration(averageOffset + delay))
    val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> theActivity._id[ObjectId]),
      Map("$set" -> Map("offset" -> Map("min" -> entryOffset._1, "max" -> entryOffset._2), "start" -> start, "end" -> end)))
    if (updateResult.getMatchedCount == 0)
      throw new IllegalArgumentException(s"MongoDB error: $updateResult")
  }

  private def setSubProcessSchedule(phase: DynDoc, calledElement: String, bpmnName: String, entryOffset: (Long, Long),
      exitOffset: (Long, Long)): Unit = {
    val start = ms2duration((entryOffset._1 + entryOffset._2) / 2)
    val end = ms2duration((exitOffset._1 + exitOffset._2) / 2)
    val bpmnTimestamps: Seq[DynDoc] = phase.bpmn_timestamps[Many[Document]]
    val idx = bpmnTimestamps.indexWhere(ts => ts.name[String] == calledElement && ts.parent_name[String] == bpmnName)
    val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phase._id[ObjectId]), Map("$set" ->
      Map(s"bpmn_timestamps.$idx.offset" -> Map("start" -> start, "end" -> end))))
    if (updateResult.getMatchedCount == 0)
      throw new IllegalArgumentException(s"MongoDB error: $updateResult")
  }

  def scheduleBpmnElements(bpmnName: String, phaseOid: ObjectId, request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val bpmnModel = bpmnModelInstance(bpmnName)
    val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
    PhaseBpmnTraverse.scheduleBpmnElements(bpmnModel, phase, bpmnName, request, response)
  }

  def scheduleBpmnElements(topLevelBpmnModel: BpmnModelInstance, phase: DynDoc, topLevelBpmnName: String,
                           request: HttpServletRequest, response: HttpServletResponse): Unit = {

    def getTimeOffset(node: FlowNode, processOffset: (Long, Long), bpmnName: String): (Long, Long) = {

      def predecessors(node: FlowNode): Seq[FlowNode] = {
        val incomingFlows: Seq[SequenceFlow] = node.getIncoming.asScala.toSeq
        incomingFlows.map(f => if (f.getTarget == node) f.getSource else f.getTarget)
      }

      def minMin(offsets: Seq[(Long, Long)]) = (offsets.map(_._1).min, offsets.map(_._2).min)
      //def minMax(offsets: Seq[(Long, Long)]) = (offsets.map(_._1).min, offsets.map(_._2).max)
      def maxMax(offsets: Seq[(Long, Long)]) = (offsets.map(_._1).max, offsets.map(_._2).max)

      node match {
        case serviceTask: ServiceTask =>
          minMin(predecessors(serviceTask).map(n => getTimeOffset(n, processOffset, bpmnName)))
        case _: StartEvent =>
          processOffset
        case parallelGateway: ParallelGateway =>
          maxMax(predecessors(parallelGateway).map(n => getTimeOffset(n, processOffset, bpmnName)))
        case exclusiveGateway: ExclusiveGateway =>
          minMin(predecessors(exclusiveGateway).map(n => getTimeOffset(n, processOffset, bpmnName)))
        case endEvent: EndEvent =>
          val exitOffset = minMin(predecessors(endEvent).map(n => getTimeOffset(n, processOffset, bpmnName)))
          //setProcessSchedule(phase, bpmnId, processOffset, exitOffset)
          exitOffset
        case callActivity: CallActivity =>
          val entryOffset = minMin(predecessors(callActivity).map(n => getTimeOffset(n, processOffset, bpmnName)))
          if (callActivity.getCalledElement == "Infra-Activity-Handler") {
            val delay = getActivityDuration(callActivity.getId, phase, bpmnName)
            setActivitySchedule(callActivity.getId, phase, bpmnName, entryOffset, delay)
            //BWLogger.log(getClass.getName, s"timeOffset(${node.getClass.getSimpleName})", offset.toString(), request)
            val exitOffset = (entryOffset._1 + delay, entryOffset._2 + delay)
            exitOffset
          } else {
            val calledElement = callActivity.getCalledElement
            val bpmnModel2 = bpmnModelInstance(calledElement)
            val endEvents: Seq[EndEvent] = bpmnModel2.getModelElementsByType(classOf[EndEvent]).asScala.toSeq
            val exitOffset = getTimeOffset(endEvents.head, entryOffset, calledElement)
            //BWLogger.log(getClass.getName, "setSubProcessSchedule",
            //  s"calledElement: $calledElement, bpmnName: $bpmnName, entryOffset: $entryOffset, exitOffset: $exitOffset", request)
            setSubProcessSchedule(phase, calledElement, bpmnName, entryOffset, exitOffset)
            exitOffset
          }
        case ice: IntermediateCatchEvent if !ice.getChildElementsByType(classOf[TimerEventDefinition]).isEmpty =>
          val entryOffset = minMin(predecessors(ice).map(n => getTimeOffset(n, processOffset, bpmnName)))
          val ted = ice.getChildElementsByType(classOf[TimerEventDefinition]).asScala.head
          val delay = getTimerDuration(ted, phase, bpmnName)
          setTimerSchedule(ted, phase, bpmnName, entryOffset, delay)
          //BWLogger.log(getClass.getName, s"timeOffset(${node.getClass.getSimpleName})", offset.toString(), request)
          val exitOffset = (entryOffset._1 + delay, entryOffset._2 + delay)
          exitOffset
      }
    }

    val endEvents: Seq[EndEvent] = topLevelBpmnModel.getModelElementsByType(classOf[EndEvent]).asScala.toSeq
    val offset = getTimeOffset(endEvents.head, (0, 0), topLevelBpmnName)
    response.getWriter.println(bson2json(new Document("min", ms2duration(offset._1)).
      append("max", ms2duration(offset._2))))
    response.setContentType("application/json")
  }

}
