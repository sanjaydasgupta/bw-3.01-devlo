package com.buildwhiz.slack

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
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
        val user: DynDoc = payload.user[Document]
        //val channel: DynDoc = payload.channel[Document]
        SlackApi.openView(viewInvite, triggerId)
      }
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
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
      |  "callback_id": "modal-identifier",
      |  "title": {
      |    "type": "plain_text",
      |    "text": "Just a modal"
      |  },
      |  "blocks": [
      |    {
      |      "type": "section",
      |      "block_id": "section-identifier",
      |      "text": {
      |        "type": "mrkdwn",
      |        "text": "*Welcome* to ~my~ Block Kit _modal_!"
      |      },
      |      "accessory": {
      |        "type": "button",
      |        "text": {
      |          "type": "plain_text",
      |          "text": "A button",
      |        },
      |        "action_id": "button-identifier",
      |      }
      |    }
      |  ],
      |}""".stripMargin
}
