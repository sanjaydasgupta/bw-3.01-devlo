package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, BpmnUtils, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.engine.repository.{DiagramElement, DiagramLayout, DiagramNode}
import org.camunda.bpm.model.bpmn.BpmnModelInstance
import org.camunda.bpm.model.bpmn.impl.instance.ParallelGatewayImpl
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
      PhaseBpmnTraverse.traverse(bpmnModel, request, response)
//      val phaseOid = new ObjectId(parameters("phase_id"))
//      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
//      val processVariables = getVariables(phase, bpmnFileName)
//      val processTimers = getTimers(phase, bpmnFileName)
//      val processActivities = getActivities(phase, bpmnFileName)
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

object PhaseBpmnTraverse extends HttpUtils {

  private def getVariables(phase: DynDoc, processName: String): Seq[Document] = {
    val variables: Seq[DynDoc] = phase.variables[Many[Document]].filter(_.bpmn_name[String] == processName)
    variables.map(variable => {
      variable.asDoc
    })
  }

  private def getTimers(phase: DynDoc, processName: String): Seq[Document] = {
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

  private def traverse(bpmnModel: BpmnModelInstance, request: HttpServletRequest, response: HttpServletResponse): Unit = {
    def stepBack(node: FlowNode, buffer: Seq[FlowNode] = Nil): Seq[FlowNode] = node match {
      case startNode: StartEvent =>
        BWLogger.log(getClass.getName, "stepBack(startNode)", startNode.toString, request)
        (startNode +: buffer).distinct
      case parallelGateway: ParallelGatewayImpl =>
        BWLogger.log(getClass.getName, "stepBack(anyNode)", parallelGateway.toString, request)
        val incomingFlows: Seq[SequenceFlow] = parallelGateway.getIncoming.asScala.toSeq
        val targets: Seq[FlowNode] = incomingFlows.map(f => if (f.getTarget == node) f.getSource else f.getTarget)
        (parallelGateway +: (targets.flatMap(t => stepBack(t)) ++ buffer)).distinct
      case anyNode =>
        BWLogger.log(getClass.getName, "stepBack(anyNode)", anyNode.toString, request)
        val incomingFlows: Seq[SequenceFlow] = anyNode.getIncoming.asScala.toSeq
        val targets: Seq[FlowNode] = incomingFlows.map(f => if (f.getTarget == node) f.getSource else f.getTarget)
        (anyNode +: (targets.flatMap(t => stepBack(t)) ++ buffer)).distinct
    }
    val endEvents: Seq[FlowNode] = bpmnModel.getModelElementsByType(classOf[EndEvent]).asInstanceOf[Many[EndEvent]]
    val allTheNodes: Seq[FlowNode] = stepBack(endEvents.head)
    response.getWriter.println(bson2json(new Document("events",
      allTheNodes.map(n => (n.getId, n.getClass.getSimpleName)).mkString(", "))))
    response.setContentType("application/json")
  }

}
