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

  private def fileUpload(bwUser: DynDoc, threadTS: String, event: DynDoc, request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getName, "fileUpload", "ENTRY", request)
    val files: Seq[DynDoc] = event.files[Many[Document]]
    val url = files.head.url_private_download[String]
    BWLogger.log(getClass.getName, "fileUpload", s"URL: $url", request)
    BWLogger.log(getClass.getName, "fileUpload", "EXIT-OK", request)
  }

  private def handleEventCallback(postData: DynDoc, request: HttpServletRequest): Unit = {
    val event: DynDoc = postData.event[Document]
    val channel = event.channel[String]
    (event.get[String]("user"), event.get[String]("thread_ts"), event.`type`[String], event.get[String]("subtype")) match {
      case (Some(slackUserId), None, "message", None) =>
        userBySlackId(slackUserId) match {
          case Some(bwUser) =>
            val commandResult = CommandLineProcessor.process(event.text[String], bwUser, event)
            SlackApi.sendToChannel(commandResult, channel, None, Some(request))
          case None =>
            val message = s"Slack-id '$slackUserId' is not registered with BuildWhiz"
            SlackApi.sendToChannel(Left(s"Your $message"), channel, None, Some(request))
            BWLogger.log(getClass.getName, "handleEventCallback", s"ERROR ($message)", request)
        }
      case (Some(slackUserId), Some(threadTs), "message", Some("file_share")) =>
        userBySlackId(slackUserId) match {
          case Some(bwUser) =>
            fileUpload(bwUser, threadTs, event, request)
          case None =>
            val message = s"Slack-id '$slackUserId' is not registered with BuildWhiz"
            SlackApi.sendToChannel(Left(s"Your $message"), channel, None, Some(request))
            BWLogger.log(getClass.getName, "handleEventCallback", s"ERROR ($message)", request)
        }
      case (_, _, "message", Some("message_changed")) =>
        BWLogger.log(getClass.getName, "handleEventCallback", "'message_changed' ignored", request)
      case (_, _, "message", Some("message_deleted")) =>
        BWLogger.log(getClass.getName, "handleEventCallback", "'message_deleted' ignored", request)
      case (_, _, "message", Some("bot_message")) =>
        BWLogger.log(getClass.getName, "handleEventCallback", "'bot_message' ignored", request)
      case _ =>
        SlackApi.sendToChannel(Left(s"Received bad message - Not handled"), channel, None, Some(request))
        BWLogger.log(getClass.getName, "handleEventCallback", s"ERROR (bad message): ${postData.asDoc.toJson}")
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val postDataStream = getStreamData(request)
    try {
      val postData: DynDoc = Document.parse(postDataStream)
      if (postData.has("challenge")) {
        val challenge = postData.challenge[String]
        response.getWriter.print(challenge)
        response.setContentType("text/plain")
        BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK (handled challenge)")
      } else if (postData.has("type") && postData.`type`[String] == "event_callback") {
        handleEventCallback(postData, request)
        BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
      } else {
        BWLogger.log(getClass.getName, request.getMethod, s"EXIT-ERROR (unknown message type): $postDataStream")
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod,
            s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage}): $postDataStream")
        //t.printStackTrace()
        throw t
    }
  }
}
