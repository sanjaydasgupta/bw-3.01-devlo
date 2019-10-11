package com.buildwhiz.baf2

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

  private def handleCallback(event: DynDoc, request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getName, "handleCallback", "ENTRY", request)
    val innerEvent: DynDoc = event.event[Document]
    if (innerEvent.has("user") && !innerEvent.has("bot_id")) {
      val slackUserId = innerEvent.user[String]
      userBySlackId(slackUserId) match {
        case Some(user: DynDoc) =>
          innerEvent.`type`[String] match {
            case "message" =>
              val commandResult = CommandLineProcessor.process(innerEvent.text[String], user)
              SlackApi.sendToChannel(commandResult, innerEvent.channel[String], Some(request))
            case eventType =>
              BWLogger.log(getClass.getName, "handleCallback",
                  s"Inner-Event type=$eventType NOT handled", request)
              SlackApi.sendToChannel(s"Received event type '$eventType' - Not handled",
                innerEvent.channel[String], Some(request))
          }
        case None =>
          SlackApi.sendToChannel(s"Your Slack-Id ($slackUserId) is not registered with BuildWhiz",
            innerEvent.channel[String], Some(request))
      }
      BWLogger.log(getClass.getName, "handleCallback", "EXIT-OK", request)
    } else
      BWLogger.log(getClass.getName, "handleCallback", "EXIT (Message Dropped)", request)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val postData = getStreamData(request)
    BWLogger.log(getClass.getName, request.getMethod, s"Post-body: $postData", request)
    try {
      val parameters: DynDoc = Document.parse(postData)
      if (parameters.has("challenge")) {
        val challenge = parameters.challenge[String]
        response.getWriter.print(challenge)
        response.setContentType("text/plain")
      } else if (parameters.has("type")) {
        val eventType = parameters.`type`[String]
        eventType match {
          case "event_callback" =>
            handleCallback(parameters, request)
          case _ =>
            BWLogger.log(getClass.getName, request.getMethod, s"Event type=$eventType NOT handled", request)
        }
      }
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
