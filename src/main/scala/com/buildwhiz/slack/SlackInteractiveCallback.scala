package com.buildwhiz.slack

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document

class SlackInteractiveCallback extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

//  private def userBySlackId(slackUserId: String): Option[DynDoc] = {
//    BWMongoDB3.persons.find(Map("slack_id" -> slackUserId)).headOption
//  }
//
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val payload: DynDoc = Document.parse(parameters("payload"))
      if (payload.has("trigger_id") && payload.`type`[String] == "message_action") {
        val triggerId = payload.trigger_id[String]

//        val rootOptions = makeSelectInputBlock("Select operation area", "Select option", "BW-root",
//          Seq(("Projects ...", "projects"), ("Tasks", "tasks"), ("Alerts", "alerts")))
//        val rootModalView = makeModalView("BuildWhiz User Interface", "BW-root", Seq(rootOptions))
        SlackApi.openView(viewRoot, triggerId)
      } else if (payload.has("trigger_id") && payload.`type`[String] == "view_submission") {
        val triggerId = payload.trigger_id[String]
        val view: DynDoc = payload.view[Document]
        val state: Document = view.state[Document]
        val stateJson = state.toJson
        BWLogger.log(getClass.getName, request.getMethod, s"state.toJson: $stateJson")
        if (stateJson.contains("BW-root-tasks")) {
//          val taskOptions = makeSelectInputBlock("Select a task", "Select task", "BW-tasks",
//            Seq(("Task A", "task-a"), ("Task B", "task-b"), ("Task C", "task-c"), ("Task D", "task-d")))
//          val tasksModalView = makeModalView("Select Task", "BW-tasks", Seq(taskOptions))
          SlackApi.pushView(viewTasks, triggerId)
        } else if (stateJson.contains("BW-root-projects")) {
//          val projectOptions = makeSelectInputBlock("Select a project", "Select project", "BW-projects",
//            Seq(("Project A1", "project-A1"), ("Project B2", "project-B2")))
//          val projectsModalView = makeModalView("Select Project", "BW-projects", Seq(projectOptions))
          SlackApi.pushView(viewTasks, triggerId)
        } else if (stateJson.contains("BW-root-alerts")) {
//          val alertOptions = makeSelectInputBlock("Select an alert", "Select alert", "BW-alerts",
//            Seq(("Alert X22", "alert-X22"), ("Alert ABC33", "alert-ABC33")))
//          val alertsModalView = makeModalView("Select Alert", "BW-alerts", Seq(alertOptions))
          SlackApi.pushView(viewTasks, triggerId)
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

  def makeModalView(title: String, id: String, blocks: Seq[Map[String, _]]): Document = {
    Map(
      "type" -> "modal",
      "callback_id" -> s"$id-modal",
      "title" -> Map("type" -> "plain_text", "text" -> title),
      "submit" -> Map("type" -> "plain_text", "text" -> "Submit", "emoji" -> true),
      "close" -> Map("type" -> "plain_text", "text" -> "Cancel", "emoji" -> true),
      "blocks" -> blocks
    )
  }

  def makeSelectInputBlock(label: String, placeHolderText: String, id: String, options: Seq[(String, String)]):
      Map[String, _] = {
    Map(
      "type" -> "input",
      "block_id" -> s"$id-block",
      "label"-> Map("type" -> "plain_text", "text" -> label),
      "element"-> Map(
        "type" -> "static_select",
        "placeholder" -> Map("type" -> "plain_text", "text" -> placeHolderText),
        "action_id" -> s"$id-options",
        "options" -> options.map(option =>
            Map("text" -> Map("type" -> "plain_text", "text" -> option._1), "value" -> s"$id-${option._2}"))
      )
    )
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
      |  "type": "modal",
      |  "callback_id": "BW-tasks",
      |  "title": {
      |    "type": "plain_text",
      |    "text": "Active Tasks List"
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
      |      "block_id": "BW-tasks-block",
      |      "label": {
      |        "type": "plain_text",
      |        "text": "Select desired task"
      |      },
      |      "element": {
      |        "type": "static_select",
      |        "placeholder": {
      |          "type": "plain_text",
      |          "text": "Select task"
      |        },
      |        "action_id": "BW-tasks-options",
      |        "options": [
      |          {
      |            "text": {
      |              "type": "plain_text",
      |              "text": "Task Alpha"
      |            },
      |            "value": "BW-tasks-alpha"
      |          },
      |          {
      |            "text": {
      |              "type": "plain_text",
      |              "text": "Task Beta"
      |            },
      |            "value": "BW-tasks-beta"
      |          },
      |          {
      |            "text": {
      |              "type": "plain_text",
      |              "text": "Task Gamma"
      |            },
      |            "value": "BW-tasks-gamma"
      |          }
      |        ]
      |      }
      |    }
      |  ]
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
