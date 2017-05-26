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
      PhaseBpmnTraverse.traverse(bpmnModel, phase, bpmnFileName, request, response)
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

  private def traverse(topLevelBpmnModel: BpmnModelInstance, phase: DynDoc, topLevelBpmnId: String,
      request: HttpServletRequest, response: HttpServletResponse): Unit = {

    def predecessors(node: FlowNode): Seq[FlowNode] = {
      val incomingFlows: Seq[SequenceFlow] = node.getIncoming.asScala.toSeq
      incomingFlows.map(f => if (f.getTarget == node) f.getSource else f.getTarget)
    }

    def minMax(offsets: Seq[(Long, Long)]) = (offsets.map(_._1).min, offsets.map(_._2).max)
    def maxMax(offsets: Seq[(Long, Long)]) = (offsets.map(_._1).max, offsets.map(_._2).max)

    def timeOffset(node: FlowNode, processOffset: (Long, Long), bpmnId: String): (Long, Long) = {
      BWLogger.log(getClass.getName, s"timeOffset(ENTRY, ${node.getClass.getSimpleName})", processOffset.toString(), request)
      node match {
        case serviceTask: ServiceTask =>
          minMax(predecessors(serviceTask).map(n => timeOffset(n, processOffset, bpmnId)))
        case _: StartEvent =>
          processOffset
        case parallelGateway: ParallelGateway =>
          maxMax(predecessors(parallelGateway).map(n => timeOffset(n, processOffset, bpmnId)))
        case exclusiveGateway: ExclusiveGateway =>
          minMax(predecessors(exclusiveGateway).map(n => timeOffset(n, processOffset, bpmnId)))
        case endEvent: EndEvent =>
          minMax(predecessors(endEvent).map(n => timeOffset(n, processOffset, bpmnId)))
        case callActivity: CallActivity =>
          val callActivityOffset = minMax(predecessors(callActivity).map(n => timeOffset(n, processOffset, bpmnId)))
          if (callActivity.getCalledElement == "Infra-Activity-Handler") {
            val delay = activityDuration(callActivity.getId, phase, bpmnId)
            val offset = (callActivityOffset._1 + delay, callActivityOffset._2 + delay)
            BWLogger.log(getClass.getName, s"timeOffset(${node.getClass.getSimpleName})", offset.toString(), request)
            offset
          } else {
            val bpmnModel2 = bpmnModelInstance(callActivity.getCalledElement)
            val endEvents: Seq[EndEvent] = bpmnModel2.getModelElementsByType(classOf[EndEvent]).asScala.toSeq
            val offset = timeOffset(endEvents.head, callActivityOffset, callActivity.getCalledElement)
            BWLogger.log(getClass.getName, s"timeOffset(${node.getClass.getSimpleName})", offset.toString(), request)
            offset
          }
        case ice: IntermediateCatchEvent if !ice.getChildElementsByType(classOf[TimerEventDefinition]).isEmpty =>
          val iceOffset = minMax(predecessors(ice).map(n => timeOffset(n, processOffset, bpmnId)))
          val ted = ice.getChildElementsByType(classOf[TimerEventDefinition]).asScala.head
          val delay = timerDuration(ted, phase, bpmnId)
          val offset = (iceOffset._1 + delay, iceOffset._2 + delay)
          BWLogger.log(getClass.getName, s"timeOffset(${node.getClass.getSimpleName})", offset.toString(), request)
          offset
      }
    }

    val endEvents: Seq[EndEvent] = topLevelBpmnModel.getModelElementsByType(classOf[EndEvent]).asScala.toSeq
    val offset = timeOffset(endEvents.head, (0, 0), topLevelBpmnId)
    response.getWriter.println(bson2json(new Document("min", offset._1).append("max", offset._2)))
    response.setContentType("application/json")
  }

}
