package com.buildwhiz.slack

import com.buildwhiz.baf2.ActivityApi
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class SlackInteractiveCallback extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val payload: DynDoc = Document.parse(parameters("payload"))
      val slackUserInfo: DynDoc = payload.user[Document]
      val slackUserId = slackUserInfo.id[String]
      val bwUserRecord: DynDoc = SlackApi.userBySlackId(slackUserId) match {
        case Some(userRecord) => userRecord
        case None =>
          val slackUserName = slackUserInfo.name[String]
          throw new IllegalArgumentException(s"Slack user '$slackUserName' ($slackUserId) unknown to BuildWhiz")
      }
      if (payload.has("trigger_id") && payload.`type`[String] == "message_action") {
        val triggerId = payload.trigger_id[String]
        val rootOptions = SlackApi.createSelectInputBlock("Select operation area", "Select option", "BW-root",
          Seq(("Dashboard", "dashboard"), ("Tasks", "tasks")))
        val rootModalView = SlackApi.createModalView("BuildWhiz User Interface", "BW-root", Seq(rootOptions))
        SlackApi.viewOpen(rootModalView.toJson, triggerId)
      } else if (payload.has("trigger_id") && payload.`type`[String] == "view_submission") {
        val triggerId = payload.trigger_id[String]
        val view: DynDoc = payload.view[Document]
        val state: DynDoc = view.state[Document]
        val stateJson = state.asDoc.toJson
        BWLogger.log(getClass.getName, request.getMethod, s"state.toJson: $stateJson")
        if (stateJson.contains("BW-root-tasks")) {
          val taskViewMessage = SlackApi.createTaskSelectionView(bwUserRecord)
          val responseText = taskViewMessage.toJson
          BWLogger.log(getClass.getName, request.getMethod, s"response: $responseText")
          response.getWriter.println(responseText)
          response.setContentType("application/json")
        } else if (stateJson.contains("BW-root-dashboard")) {
          val dashboardViewMessage = SlackApi.createDashboardView(bwUserRecord)
          val responseText = dashboardViewMessage.toJson
          BWLogger.log(getClass.getName, request.getMethod, s"response: $responseText")
          response.getWriter.println(responseText)
          response.setContentType("application/json")
        } else if (stateJson.contains("BW-tasks-update-display")) {
          val activityOid = new ObjectId(stateJson.split("-").last.substring(0, 24))
          val theActivity = ActivityApi.activityById(activityOid)
          val taskStatusUpdateViewMessage = SlackApi.createTaskStatusUpdateView(bwUserRecord, theActivity)
          val responseText = taskStatusUpdateViewMessage.toJson
          BWLogger.log(getClass.getName, request.getMethod, s"response: $responseText")
          response.getWriter.println(responseText)
          response.setContentType("application/json")
        } else if (stateJson.contains("BW-tasks-update-completion-date")) {
          val values = state.values[Document]
          val optimisticCompletionBlock = values.get("BW-tasks-update-completion-date-optimistic-block").asInstanceOf[Document]
          val optimisticCompletionDatepicker = optimisticCompletionBlock.get("BW-tasks-update-completion-date-optimistic").asInstanceOf[Document]
          val optimisticCompletionDate = optimisticCompletionDatepicker.getString("selected_date")
          val pessimisticCompletionBlock = values.get("BW-tasks-update-completion-date-pessimistic-block").asInstanceOf[Document]
          val pessimisticCompletionDatepicker = pessimisticCompletionBlock.get("BW-tasks-update-completion-date-pessimistic").asInstanceOf[Document]
          val pessimisticCompletionDate = pessimisticCompletionDatepicker.getString("selected_date")
          val likelyCompletionBlock = values.get("BW-tasks-update-completion-date-likely-block").asInstanceOf[Document]
          val likelyCompletionDatepicker = likelyCompletionBlock.get("BW-tasks-update-completion-date-likely").asInstanceOf[Document]
          val likelyCompletionDate = likelyCompletionDatepicker.getString("selected_date")
          val percentCompleteBlock = values.get("BW-tasks-update-percent-complete-block").asInstanceOf[Document]
          val percentCompleteInput = percentCompleteBlock.get("BW-tasks-update-percent-complete").asInstanceOf[Document]
          val percentCompleteValue = percentCompleteInput.getString("value")
          val completionCommentsBlock = values.get("BW-tasks-update-comments-block").asInstanceOf[Document]
          val completionCommentsInput = completionCommentsBlock.get("BW-tasks-update-comments").asInstanceOf[Document]
          val completionCommentsValue = completionCommentsInput.getString("value")
          val message = s"optimistic: $optimisticCompletionDate, pessimistic: $pessimisticCompletionDate, " +
            s"likely: $likelyCompletionDate, % complete: $percentCompleteValue, comments: $completionCommentsValue"
          BWLogger.log(getClass.getName, request.getMethod, s"Received values: $message")
        } else {
        }
      } else if (payload.`type`[String] == "block_action") {
      }
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK")
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object SlackInteractiveCallbackTest extends App {

  val taskOptions = SlackApi.createSelectInputBlock("Select a task", "Select task", "BW-tasks",
    Seq(("Task A", "task-a"), ("Task B", "task-b"), ("Task C", "task-c"), ("Task D", "task-d")))
  val tasksModalView = SlackApi.createModalView("Select Task", "BW-tasks", Seq(taskOptions))
  val viewMessage: Document = Map("view" -> tasksModalView, "response_action" -> "push")
  val responseText = viewMessage.toJson

  println(responseText)
}
