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

  private def replyToUser(messageText: String, eventChannel: String, optUser: Option[DynDoc],
      request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getName, "replyToUser", "ENTRY", request)
    val httpClient = HttpClients.createDefault()
    val post = new HttpPost("https://slack.com/api/chat.postMessage")
    post.setHeader("Authorization",
        //"Bearer xoxp-644537296277-644881565541-687602244033-a112c341c2a73fe62b1baf98d9304c1f")
      "Bearer xoxb-644537296277-708634256516-vIeyFBxDJVd0aBJHts5EoLCp")
    post.setHeader("Content-Type", "application/json")
    val bwUserName = optUser match {
      case Some(user) => s"${user.first_name[String]} ${user.last_name[String]}"
      case None => "Unknown User"
    }
    val eventText = if (optUser.isDefined)
      s"Hi $bwUserName, This is a reply to: $messageText"
    else
      s"Hi $bwUserName, $messageText"
    val bodyText = s"""{"text": "$eventText", "channel": "$eventChannel"}"""
    post.setEntity(new StringEntity(bodyText, ContentType.create("plain/text", Consts.UTF_8)))
    val response = httpClient.execute(post)
    val responseContent = new ByteArrayOutputStream()
    response.getEntity.writeTo(responseContent)
    val contentString = responseContent.toString
    val statusLine = response.getStatusLine
    if (statusLine.getStatusCode != 200)
      throw new IllegalArgumentException(s"Bad chat.postMessage status: $contentString")
    BWLogger.log(getClass.getName, "replyToUser", "EXIT-OK", request)
  }

  private def userBySlackId(slackUserId: String): Option[DynDoc] = {
    BWMongoDB3.persons.find(Map("slack_id" -> slackUserId)).headOption
  }

  private def handleCallback(event: DynDoc, request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getName, "handleCallback", "ENTRY", request)
    val innerEvent: DynDoc = event.event[Document]
    if (innerEvent.has("user")) {
      val slackUserId = innerEvent.user[String]
      userBySlackId(slackUserId) match {
        case Some(user: DynDoc) =>
          innerEvent.`type`[String] match {
            case "message" =>
              replyToUser(innerEvent.text[String], innerEvent.channel[String], Some(user), request)
            case eventType =>
              BWLogger.log(getClass.getName, "handleCallback",
                  s"Inner-Event type=$eventType NOT handled", request)
              replyToUser(s"Received event type '$eventType' - Not handled",
                innerEvent.channel[String], Some(user), request)
          }
        case None =>
          replyToUser(s"Your Slack-Id ($slackUserId) is not registered with BuildWhiz",
            innerEvent.channel[String], None, request)
      }
    }
    BWLogger.log(getClass.getName, "handleCallback", "EXIT-OK", request)
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
