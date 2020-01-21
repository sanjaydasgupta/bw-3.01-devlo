package com.buildwhiz.slack

import java.io.ByteArrayOutputStream

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.utils.BWLogger
import javax.servlet.http.HttpServletRequest
import org.apache.http.Consts
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.HttpClients

import org.bson.types.ObjectId

object SlackApi {

  def invite(who: DynDoc): DynDoc => String = {
    user => {
      s"${PersonApi.fullName(user)} wishes to invite ${PersonApi.fullName(who)}"
    }
  }

  def status(who: DynDoc): DynDoc => String = {
    _ => {
      val slackStatus = if (who.has("slack_id"))
        "Connected"
      else
        "Not connected"
      s"${PersonApi.fullName(who)}'s Slack status is '$slackStatus'"
    }
  }

  def sendToUser(messageText: String, user: Either[DynDoc, ObjectId], request: Option[HttpServletRequest] = None):
      Unit = {
    BWLogger.log(getClass.getName, "sendToUser", "ENTRY", request)
    val personRecord: DynDoc = user match {
      case Left(dd) => dd
      case Right(oid) => PersonApi.personById(oid)
    }
    if (personRecord.has("slack_id")) {
      val slackChannel = personRecord.slack_id[String]
      sendToChannel(messageText, slackChannel, request)
      BWLogger.log(getClass.getName, "sendToUser", "EXIT-OK", request)
    } else {
      val message = s"ERROR: User ${PersonApi.fullName(personRecord)} not on Slack. Message dropped: '$messageText'"
      BWLogger.log(getClass.getName, "sendToUser", message, request)
    }
  }

  def sendToChannel(messageText: String, channel: String, request: Option[HttpServletRequest] = None): Unit = {
    BWLogger.log(getClass.getName, "sendToChannel", "ENTRY", request)
    val httpClient = HttpClients.createDefault()
    val post = new HttpPost("https://slack.com/api/chat.postMessage")
    post.setHeader("Authorization",
      //"Bearer xoxp-644537296277-644881565541-687602244033-a112c341c2a73fe62b1baf98d9304c1f")
      "Bearer xoxb-644537296277-708634256516-vIeyFBxDJVd0aBJHts5EoLCp")
    post.setHeader("Content-Type", "application/json")
    val bodyText = s"""{"text": "$messageText", "channel": "$channel"}"""
    post.setEntity(new StringEntity(bodyText, ContentType.create("plain/text", Consts.UTF_8)))
    val response = httpClient.execute(post)
    val responseContent = new ByteArrayOutputStream()
    response.getEntity.writeTo(responseContent)
    val contentString = responseContent.toString
    val statusLine = response.getStatusLine
    if (statusLine.getStatusCode != 200)
      throw new IllegalArgumentException(s"Bad chat.postMessage status: $contentString")
    BWLogger.log(getClass.getName, "sendToChannel", "EXIT-OK", request)
  }

}
