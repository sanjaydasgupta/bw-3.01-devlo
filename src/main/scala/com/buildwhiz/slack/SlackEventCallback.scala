package com.buildwhiz.slack

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, CommandLineProcessor, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document

class SlackEventCallback extends HttpServlet with HttpUtils {

  private def userBySlackId(slackUserId: String): Option[DynDoc] = {
    BWMongoDB3.persons.find(Map("slack_id" -> slackUserId)).headOption
  }

  private def handleEventCallback(postData: DynDoc, request: HttpServletRequest): Unit = {
    val event: DynDoc = postData.event[Document]
    if (event.has("user")) {
      val slackUserId = event.user[String]
      val channel = event.channel[String]
      val timeStamp = event.ts[String]
      userBySlackId(slackUserId) match {
        case Some(user: DynDoc) =>
          event.`type`[String] match {
            case "message" =>
              if (event.has("subtype")) {
                // subtype values: bot_message, file_share, message_deleted, message_changed
              } else {
                val commandResult = CommandLineProcessor.process(event.text[String], user, event)
                SlackApi.sendToChannel(commandResult, channel, Some(timeStamp), Some(request))
              }
            case eventType =>
              BWLogger.log(getClass.getName, "handleEventCallback",
                  s"EXIT-ERROR (bad event type): ${event.asDoc.toJson}")
              SlackApi.sendToChannel(Left(s"Received event type '$eventType' - Not handled"), channel, Some(timeStamp),
                Some(request))
          }
        case None =>
          SlackApi.sendToChannel(Left(s"Your Slack-Id ($slackUserId) is not registered with BuildWhiz"),
            channel, Some(timeStamp), Some(request))
      }
      BWLogger.log(getClass.getName, "handleEventCallback", "EXIT-OK")
    } else if (event.has("bot_id")) {
      // Message already logged, no particular action
    } else {
      BWLogger.log(getClass.getName, "handleEventCallback", s"EXIT-ERROR (unknown message): ${event.asDoc.toJson}")
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val postDataStream = getStreamData(request)
    BWLogger.log(getClass.getName, request.getMethod, s"Post-body: $postDataStream", request)
    try {
      val postData: DynDoc = Document.parse(postDataStream)
      if (postData.has("challenge")) {
        val challenge = postData.challenge[String]
        response.getWriter.print(challenge)
        response.setContentType("text/plain")
        BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK (handled challenge)")
      } else if (postData.has("type")) {
        val eventType = postData.`type`[String]
        eventType match {
          case "event_callback" =>
            handleEventCallback(postData, request)
          case _ =>
            BWLogger.log(getClass.getName, request.getMethod, s"EXIT-ERROR (unknown 'type' field): $postDataStream")
        }
      } else {
        BWLogger.log(getClass.getName, request.getMethod, s"EXIT-ERROR (missing 'type' field): $postDataStream")
      }
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod,
            s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage}): $postDataStream")
        //t.printStackTrace()
        throw t
    }
  }
}
