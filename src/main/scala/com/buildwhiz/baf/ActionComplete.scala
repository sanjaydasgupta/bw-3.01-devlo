package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import BWMongoDB3._
import com.buildwhiz.HttpUtils
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines

import scala.collection.JavaConverters._

class ActionComplete extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val activityQuery = Map("_id" -> new ObjectId(parameters("activity_id")))
      val activity: DynDoc = BWMongoDB3.activities.find(activityQuery).asScala.head
      val actions: Seq[DynDoc] = activity.actions[DocumentList]
      val actionsWithIndex = actions.zipWithIndex
      val actionName = parameters("action_name")
      actionsWithIndex.find(_._1.name[String] == actionName) match {
        case Some((action, idx)) =>
          val hasId = action ? "camunda_execution_id"
          if (hasId) {
            val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
            BWLogger.log(getClass.getName, "doPost", "calling messageEventReceived()", request)
            rts.messageEventReceived("Action-Complete", action.camunda_execution_id[String])
          }
          val status = s"actions.$idx.status" -> (if (hasId) "ended" else "ready")
          val timestamp = s"actions.$idx.timestamps.end" -> System.currentTimeMillis
          val newValues = action.`type`[String] match {
            case "review" =>
              val reviewResult = s"actions.$idx.review_ok" -> (parameters("review_ok") == "OK")
              Seq(reviewResult, status, timestamp)
            case _ =>
              Seq(status, timestamp)
          }
          val updateResult = BWMongoDB3.activities.updateOne(activityQuery, Map("$set" -> newValues.toMap))
          if (updateResult.getModifiedCount == 0)
            throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

          val theActivity: DynDoc = BWMongoDB3.activities.find(activityQuery).asScala.head
          val allActionsComplete = theActivity.actions[DocumentList].forall(_.status[String] == "ended")

          Thread.sleep(500)

          val thePhase: DynDoc = BWMongoDB3.phases.find(Map("activity_ids" -> theActivity._id[ObjectId])).asScala.head
          val bpmnName = theActivity.bpmn_name[String]
          val allActivitiesComplete = thePhase.bpmn_timestamps[DocumentList].
            exists(bts => bts.name[String] == bpmnName && bts.event[String] == "end")

          val topBpmnName = thePhase.bpmn_name[String]
          val allProcessesComplete = thePhase.bpmn_timestamps[DocumentList].
            exists(bts => bts.name[String] == topBpmnName && bts.event[String] == "end")

          if (this != ActionComplete) {
            response.getWriter.println(s"""{"all_actions_complete": $allActionsComplete, "all_activities_complete": """ +
              s"""$allActivitiesComplete, "all_processes_complete": $allProcessesComplete}""")
            response.setContentType("application/json")
          }
        case _ =>
          throw new IllegalArgumentException(s"Action '$actionName' NOT found")
      }
      BWLogger.log(getClass.getName, "doPost", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}

object ActionComplete extends ActionComplete
