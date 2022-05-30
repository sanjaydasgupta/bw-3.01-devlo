package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines

class ActionComplete extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val activityQuery = Map("_id" -> new ObjectId(parameters("activity_id")))
      val activity: DynDoc = BWMongoDB3.tasks.find(activityQuery).head
      val actions: Seq[DynDoc] = activity.actions[Many[Document]]
      val actionsWithIndex = actions.zipWithIndex
      val actionName = parameters("action_name")
      actionsWithIndex.find(_._1.name[String] == actionName) match {
        case Some((action, idx)) =>
          val hasId = action has "camunda_execution_id"
          if (hasId) {
            val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
            BWLogger.log(getClass.getName, request.getMethod, "calling messageEventReceived()", request)
            rts.messageEventReceived("Action-Complete", action.camunda_execution_id[String])
          }
          val completionMessage = s"actions.$idx.completion_message" ->
            (if (parameters.contains("completion_message")) parameters("completion_message") else "-")
          val status = s"actions.$idx.status" -> (if (hasId) "ended" else "ready")
          val timestamp = s"actions.$idx.timestamps.end" -> System.currentTimeMillis
          val newValues = action.`type`[String] match {
            case "review" =>
              val reviewResult = s"actions.$idx.review_ok" ->
                (if (parameters.contains("review_ok")) parameters("review_ok") == "OK" else false)
              Seq(reviewResult, status, completionMessage, timestamp)
            case _ =>
              Seq(status, completionMessage, timestamp)
          }
          val updateResult = BWMongoDB3.tasks.updateOne(activityQuery, Map("$set" -> newValues.toMap))
          if (updateResult.getModifiedCount == 0)
            throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

          val theActivity: DynDoc = BWMongoDB3.tasks.find(activityQuery).head
          val allActionsComplete = theActivity.actions[Many[Document]].forall(_.status[String] == "ended")

          Thread.sleep(500)

          val thePhase: DynDoc = BWMongoDB3.processes.find(Map("activity_ids" -> theActivity._id[ObjectId])).head
          val bpmnName = theActivity.bpmn_name[String]
          val topBpmnName = thePhase.bpmn_name[String]
          val allProcessesComplete = thePhase.bpmn_timestamps[Many[Document]].
            exists(bts => bts.name[String] == topBpmnName && bts.parent_name[String] == "" &&
              bts.status[String] == "ended")

          val allActivitiesComplete = if (allProcessesComplete)
            true
          else
            thePhase.bpmn_timestamps[Many[Document]].
            exists(bts => bts.name[String] == bpmnName && bts.parent_name[String] != "" &&
              bts.status[String] == "ended")

          if (this != ActionComplete) {
            response.getWriter.println(s"""{"all_actions_complete": $allActionsComplete, "all_activities_complete": """ +
              s"""$allActivitiesComplete, "all_processes_complete": $allProcessesComplete}""")
            response.setContentType("application/json")
          }
        case _ =>
          throw new IllegalArgumentException(s"Action '$actionName' NOT found")
      }
      val actionLog = s"'$actionName'"
      BWLogger.audit(getClass.getName, request.getMethod, s"""Completed action $actionLog""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object ActionComplete extends ActionComplete
