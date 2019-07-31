package com.buildwhiz.baf2

import java.io.ByteArrayOutputStream

import org.apache.http.Consts
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.HttpClients
import org.bson.Document

class SlackEventCallback extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  private def replyToUser(innerEvent: DynDoc, user: DynDoc, request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "replyToUser", "ENTRY", request)
    val httpClient = HttpClients.createDefault()
    val post = new HttpPost("https://slack.com/api/chat.postMessage")
    post.setHeader("Authorization",
        //"Bearer xoxp-644537296277-644881565541-687602244033-a112c341c2a73fe62b1baf98d9304c1f")
      "Bearer xoxb-644537296277-708634256516-vIeyFBxDJVd0aBJHts5EoLCp")
    post.setHeader("Content-Type", "application/json")
    val bwUserName = s"${user.first_name[String]} ${user.last_name[String]}"
    val eventText = s"Hi $bwUserName, This is a reply to: ${innerEvent.text[String]}"
    val eventChannel = innerEvent.channel[String]
    val bodyText = s"""{"text": "$eventText", "channel": "$eventChannel"}"""
    post.setEntity(new StringEntity(bodyText, ContentType.create("plain/text", Consts.UTF_8)))
    val response = httpClient.execute(post)
    val statusLine = response.getStatusLine
    if (statusLine.getStatusCode != 200)
      throw new IllegalArgumentException(s"Bad chat.postMessage status: $statusLine")
    val content = new ByteArrayOutputStream()
    response.getEntity.writeTo(content)
    val contentString = content.toString
    if (contentString.nonEmpty)
      BWLogger.log(getClass.getName, "replyToUser", s"Response-Content: $contentString", request)
    BWLogger.log(getClass.getName, "replyToUser", "EXIT-OK", request)
  }

  private def userBySlackId(slackUserId: String): Option[DynDoc] = {
    BWMongoDB3.persons.find(Map("slack_id" -> slackUserId)).headOption
  }

  private def handleCallback(event: DynDoc, request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "handleCallback", "ENTRY", request)
    val innerEvent: DynDoc = event.event[Document]
    if (innerEvent.has("user")) {
      val slackUserId = innerEvent.user[String]
      userBySlackId(slackUserId) match {
        case Some(user: DynDoc) =>
          innerEvent.`type`[String] match {
            case "message" =>
              replyToUser(innerEvent, user, request, response)
            case messageType =>
              response.getWriter.print(s"")
              response.getWriter.print(s"""{"text": "Received event type '$messageType' - Not handled"}""")
              response.setContentType("application/json")
          }
          response.setContentType("application/json")
        case None =>
          response.getWriter.print(s"""{"text": "Hi $slackUserId, your Slack-Id is not registered""" +
            """ and your message was ignored"}""")
          response.setContentType("application/json")
      }
    }
    BWLogger.log(getClass.getName, "handleCallback", "EXIT-OK", request)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val postData = getStreamData(request)
    BWLogger.log(getClass.getName, "handleEvent", s"Post-body: $postData", request)
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
            handleCallback(parameters, request, response)
          case _ =>
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
