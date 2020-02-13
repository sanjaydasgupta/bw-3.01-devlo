package com.buildwhiz.slack

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document

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
        val state: Document = view.state[Document]
        val stateJson = state.toJson
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

  private val viewMessage: Document = Map(
    "type" -> "modal",
    "callback_id" -> "BW-root-modal",
    "title" -> Map("type" -> "plain_text", "text" -> "BuildWhiz User Interface"),
    "submit" -> Map("type" -> "plain_text", "text" -> "Submit", "emoji" -> true),
    "close" -> Map("type" -> "plain_text", "text" -> "Cancel", "emoji" -> true),
    "blocks" -> Seq(
      Map(
        "type" -> "input",
        "block_id" -> "BW-root-block",
        "label"-> Map("type" -> "plain_text", "text" -> "Select desired area of operation"),
        "element"-> Map(
          "type" -> "static_select",
          "placeholder" -> Map("type" -> "plain_text", "text" -> "Select area"),
          "action_id" -> "BW-root-options",
          "options" -> Seq(
            Map("text" -> Map("type" -> "plain_text", "text" -> "Projects, ..."), "value" -> "BW-root-projects"),
            Map("text" -> Map("type" -> "plain_text", "text" -> "Tasks"), "value" -> "BW-root-tasks"),
            Map("text" -> Map("type" -> "plain_text", "text" -> "Alerts"), "value" -> "BW-root-alerts"),
          )
        )
      )
    )
  )

  private val viewTasks =
    """{
      |  "response_action": "push",
      |  "view": {
      |    "type": "modal",
      |    "callback_id": "BW-tasks",
      |    "title": {
      |      "type": "plain_text",
      |      "text": "Active Tasks List"
      |    },
      |    "submit": {
      |      "type": "plain_text",
      |      "text": "Submit",
      |      "emoji": true
      |    },
      |    "close": {
      |      "type": "plain_text",
      |      "text": "Cancel",
      |      "emoji": true
      |    },
      |    "blocks": [
      |      {
      |        "type": "input",
      |        "block_id": "BW-tasks-block",
      |        "label": {
      |          "type": "plain_text",
      |          "text": "Select desired task"
      |        },
      |        "element": {
      |          "type": "static_select",
      |          "placeholder": {
      |            "type": "plain_text",
      |            "text": "Select task"
      |          },
      |          "action_id": "BW-tasks-options",
      |          "options": [
      |            {
      |              "text": {
      |                "type": "plain_text",
      |                "text": "Task Alpha"
      |              },
      |              "value": "BW-tasks-alpha"
      |            },
      |            {
      |              "text": {
      |                "type": "plain_text",
      |                "text": "Task Beta"
      |              },
      |              "value": "BW-tasks-beta"
      |            },
      |            {
      |              "text": {
      |                "type": "plain_text",
      |                "text": "Task Gamma"
      |              },
      |              "value": "BW-tasks-gamma"
      |            }
      |          ]
      |        }
      |      }
      |    ]
      |  }
      |}""".stripMargin

  private val viewRoot =
    """{
      |  "type": "modal",
      |  "callback_id": "BW-root",
      |  "title": {
      |    "type": "plain_text",
      |    "text": "BuildWhiz User Interface"
      |  },
      |  "submit": {
      |    "type": "plain_text",
      |    "text": "Submit",
      |    "emoji": true
      |  },
      |  "close": {
      |    "type": "plain_text",
      |    "text": "Cancel",
      |    "emoji": true
      |  },
      |  "blocks": [
      |    {
      |      "type": "input",
      |      "block_id": "BW-root-block",
      |      "label": {
      |        "type": "plain_text",
      |        "text": "Select desired area of operation"
      |      },
      |      "element": {
      |        "type": "static_select",
      |        "placeholder": {
      |          "type": "plain_text",
      |          "text": "Select area"
      |        },
      |        "action_id": "BW-root-options",
      |        "options": [
      |          {
      |            "text": {
      |              "type": "plain_text",
      |              "text": "Projects, phases, processes"
      |            },
      |            "value": "BW-root-projects"
      |          },
      |          {
      |            "text": {
      |              "type": "plain_text",
      |              "text": "Active tasks"
      |            },
      |            "value": "BW-root-tasks"
      |          },
      |          {
      |            "text": {
      |              "type": "plain_text",
      |              "text": "Alerts"
      |            },
      |            "value": "BW-root-alerts"
      |          }
      |        ]
      |      }
      |    }
      |  ]
      |}""".stripMargin
}

object SlackInteractiveCallbackTest extends App {

  val taskOptions = SlackApi.createSelectInputBlock("Select a task", "Select task", "BW-tasks",
    Seq(("Task A", "task-a"), ("Task B", "task-b"), ("Task C", "task-c"), ("Task D", "task-d")))
  val tasksModalView = SlackApi.createModalView("Select Task", "BW-tasks", Seq(taskOptions))
  val viewMessage: Document = Map("view" -> tasksModalView, "response_action" -> "push")
  val responseText = viewMessage.toJson

  println(responseText)
}
