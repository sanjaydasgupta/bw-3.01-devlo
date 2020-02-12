package com.buildwhiz.slack

import java.io.ByteArrayOutputStream

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.BWLogger
import javax.servlet.http.HttpServletRequest
import org.apache.http.Consts
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.HttpClients
import org.bson.Document
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

  def sendToUser(stringOrBlocks: Either[String, Seq[DynDoc]], user: Either[DynDoc, ObjectId],
      request: Option[HttpServletRequest] = None): Unit = {
    BWLogger.log(getClass.getName, "sendToUser", "ENTRY")
    val personRecord: DynDoc = user match {
      case Left(dd) => dd
      case Right(oid) => PersonApi.personById(oid)
    }
    if (personRecord.has("slack_id")) {
      val slackChannel = personRecord.slack_id[String]
      sendToChannel(stringOrBlocks, slackChannel, request)
      BWLogger.log(getClass.getName, "sendToUser", "EXIT-OK", request)
    } else {
      val message = s"ERROR: User ${PersonApi.fullName(personRecord)} not on Slack. Message dropped: '$stringOrBlocks'"
      BWLogger.log(getClass.getName, "sendToUser", message)
    }
  }

  def sendToChannel(textOrBlocks: Either[String, Seq[DynDoc]], channel: String,
                    request: Option[HttpServletRequest] = None): Unit = {
    BWLogger.log(getClass.getName, "sendToChannel", "ENTRY")
    val httpClient = HttpClients.createDefault()
    val post = new HttpPost("https://slack.com/api/chat.postMessage")
    post.setHeader("Authorization",
      //"Bearer xoxp-644537296277-644881565541-687602244033-a112c341c2a73fe62b1baf98d9304c1f")
      "Bearer xoxb-644537296277-708634256516-vIeyFBxDJVd0aBJHts5EoLCp")
    post.setHeader("Content-Type", "application/json")
    val bodyText = textOrBlocks match {
      case Left(messageText) => s"""{"text": "$messageText", "channel": "$channel"}"""
      case Right(blocks) =>
        val blocksText = blocks.map(_.asDoc.toJson).mkString(",")
        s"""{"blocks": [$blocksText], "channel": "$channel"}"""
    }
    BWLogger.log(getClass.getName, "sendToChannel", s"Message: $bodyText")
    post.setEntity(new StringEntity(bodyText, ContentType.create("plain/text", Consts.UTF_8)))
    val response = httpClient.execute(post)
    val responseContent = new ByteArrayOutputStream()
    response.getEntity.writeTo(responseContent)
    val contentString = responseContent.toString
    val statusLine = response.getStatusLine
    if (statusLine.getStatusCode != 200)
      throw new IllegalArgumentException(s"Bad chat.postMessage status: $contentString")
    BWLogger.log(getClass.getName, "sendToChannel", "EXIT-OK")
  }

  // https://api.slack.com/messaging/interactivity

  def createSectionWithButton(descriptionText: String, buttonText: String, buttonValue: String): DynDoc = Map(
    "type" -> "section",
    "text" -> Map("type" -> "mrkdwn", "text" -> descriptionText),
    "accessory" -> createButton(buttonText, buttonValue)
  )

  def createButton(buttonText: String, buttonValue: String): DynDoc = Map(
    "type" -> "button",
    "text" -> Map("type" -> "plain_text", "text" -> buttonText, "emoji" -> true),
    "value" -> buttonValue
  )

  def createMultipleChoiceMessage(optionDescriptionsAndTexts: Seq[(String, String)], messageId: String):
      Seq[DynDoc] = {
    optionDescriptionsAndTexts.map(dt => createSectionWithButton(dt._1, "Select", s"$messageId-${dt._2}"))
  }

  def createModalView(title: String, id: String, blocks: Seq[Map[String, _]]): Document = {
    Map(
      "type" -> "modal",
      "callback_id" -> s"$id-modal",
      "title" -> Map("type" -> "plain_text", "text" -> title),
      "submit" -> Map("type" -> "plain_text", "text" -> "Submit", "emoji" -> true),
      "close" -> Map("type" -> "plain_text", "text" -> "Cancel", "emoji" -> true),
      "blocks" -> blocks
    )
  }

  def createSelectInputBlock(label: String, placeHolderText: String, id: String, options: Seq[(String, String)]):
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

  def viewOpen(viewText: String, triggerId: String): Unit = {
    BWLogger.log(getClass.getName, "openView", "ENTRY")
    val httpClient = HttpClients.createDefault()
    val post = new HttpPost("https://slack.com/api/views.open")
    post.setHeader("Authorization",
      //"Bearer xoxp-644537296277-644881565541-687602244033-a112c341c2a73fe62b1baf98d9304c1f")
      "Bearer xoxb-644537296277-708634256516-vIeyFBxDJVd0aBJHts5EoLCp")
    post.setHeader("Content-Type", "application/json; charset=utf-8")
    val bodyText = s"""{"view": $viewText, "trigger_id": "$triggerId"}"""
    post.setEntity(new StringEntity(bodyText, ContentType.create("plain/text", Consts.UTF_8)))
    val response = httpClient.execute(post)
    val responseContent = new ByteArrayOutputStream()
    response.getEntity.writeTo(responseContent)
    val contentString = responseContent.toString
    val statusLine = response.getStatusLine
    if (statusLine.getStatusCode != 200)
      throw new IllegalArgumentException(s"Bad views.open status: $contentString")
    BWLogger.log(getClass.getName, "openView", s"EXIT-OK ($contentString)")
  }

  def viewPush(viewText: String, triggerId: String): Unit = {
    BWLogger.log(getClass.getName, "pushView", "ENTRY")
    val httpClient = HttpClients.createDefault()
    val post = new HttpPost("https://slack.com/api/views.push")
    post.setHeader("Authorization",
      //"Bearer xoxp-644537296277-644881565541-687602244033-a112c341c2a73fe62b1baf98d9304c1f")
      "Bearer xoxb-644537296277-708634256516-vIeyFBxDJVd0aBJHts5EoLCp")
    post.setHeader("Content-Type", "application/json; charset=utf-8")
    val bodyText = s"""{"view": $viewText, "trigger_id": "$triggerId"}"""
    post.setEntity(new StringEntity(bodyText, ContentType.create("plain/text", Consts.UTF_8)))
    val response = httpClient.execute(post)
    val responseContent = new ByteArrayOutputStream()
    response.getEntity.writeTo(responseContent)
    val contentString = responseContent.toString
    val statusLine = response.getStatusLine
    if (statusLine.getStatusCode != 200)
      throw new IllegalArgumentException(s"Bad views.open status: $contentString")
    BWLogger.log(getClass.getName, "pushView", s"EXIT-OK ($contentString)")
  }

  def main(args: Array[String]): Unit = {
    val specs = Seq(("One description", "one"), ("Two description", "two"), ("Three description", "three"))
    val mcm = createMultipleChoiceMessage(specs, "message_id")
    println(mcm.map(_.asDoc.toJson).mkString(",\n"))
  }

}
