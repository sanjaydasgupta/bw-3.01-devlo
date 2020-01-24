package com.buildwhiz.slack

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document

class SlackInteractiveCallback extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  private def userBySlackId(slackUserId: String): Option[DynDoc] = {
    BWMongoDB3.persons.find(Map("slack_id" -> slackUserId)).headOption
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val payload: DynDoc = Document.parse(parameters("payload"))
      if (payload.has("trigger_id") && payload.`type`[String] == "message_action") {
        val triggerId = payload.trigger_id[String]
        SlackApi.openView(viewInvite, triggerId)
      } else if (payload.`type`[String] == "view_submission") {
        val view: DynDoc = payload.view[Document]
        val state: DynDoc = view.state[Document]
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

  private val viewInvite =
    """{
      |  "type": "modal",
      |  "callback_id": "BW-modal",
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
      |      "block_id": "BW-SELECTION-block",
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
      |        "action_id": "BW-SELECTION-options",
      |        "options": [
      |          {
      |            "text": {
      |              "type": "plain_text",
      |              "text": "Projects, phases, processes"
      |            },
      |            "value": "BW-SELECTION-projects-phases-processes"
      |          },
      |          {
      |            "text": {
      |              "type": "plain_text",
      |              "text": "Active tasks"
      |            },
      |            "value": "BW-SELECTION-active-tasks"
      |          },
      |          {
      |            "text": {
      |              "type": "plain_text",
      |              "text": "Alerts"
      |            },
      |            "value": "BW-SELECTION-alerts"
      |          }
      |        ]
      |      }
      |    }
      |  ]
      |}""".stripMargin
}
