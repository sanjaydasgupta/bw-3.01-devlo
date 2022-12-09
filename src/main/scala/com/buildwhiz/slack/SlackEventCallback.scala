package com.buildwhiz.slack

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class SlackEventCallback extends HttpServlet with HttpUtils {

//  private def fileUpload(bwUser: DynDoc, threadTS: String, event: DynDoc, request: HttpServletRequest): Unit = {
//    BWLogger.log(getClass.getName, "fileUpload", "ENTRY", request)
//    val files: Seq[DynDoc] = event.files[Many[Document]]
//    val url = files.head.url_private_download[String]
//    BWLogger.log(getClass.getName, "fileUpload", s"URL: $url", request)
//    BWLogger.log(getClass.getName, "fileUpload", "EXIT-OK", request)
//  }
//
//  private def handleEventCallback(postData: DynDoc, request: HttpServletRequest): Unit = {
//    val event: DynDoc = postData.event[Document]
//    val channel = event.channel[String]
//    (event.get[String]("user"), event.get[String]("thread_ts"), event.`type`[String], event.get[String]("subtype")) match {
//      case (Some(slackUserId), whichThread, "message", None) =>
//        SlackApi.userBySlackId(slackUserId) match {
//          case Some(bwUser) =>
//            CommandLineProcessor.process(event.text[String], bwUser, event, request) match {
//              case Some(message) => SlackApi.sendToChannel(Left(message), channel, whichThread, Some(request))
//              case None =>
//            }
//          case None =>
//            val message = s"Slack-id '$slackUserId' is not registered with BuildWhiz"
//            //SlackApi.sendToChannel(Left(s"Your $message"), channel, None, Some(request))
//            BWLogger.log(getClass.getName, "handleEventCallback", s"ERROR ($message)", request)
//        }
//      case (Some(slackUserId), Some(threadTs), "message", Some("file_share")) =>
//        SlackApi.userBySlackId(slackUserId) match {
//          case Some(bwUser) =>
//            fileUpload(bwUser, threadTs, event, request)
//          case None =>
//            val message = s"Slack-id '$slackUserId' is not registered with BuildWhiz"
//            //SlackApi.sendToChannel(Left(s"Your $message"), channel, None, Some(request))
//            BWLogger.log(getClass.getName, "handleEventCallback", s"ERROR ($message)", request)
//        }
//      case (Some(slackUserId), _, "app_home_opened", _) =>
//        BWLogger.log(getClass.getName, request.getMethod, s"handleEventCallback: ${event.asDoc.toJson}", request)
//        SlackApi.viewPublish(None, slackUserId, None)
//        event.get[String]("tab") match {
//          case Some("home") =>
//            BWLogger.log(getClass.getName, request.getMethod, s"handleEventCallback: ${event.asDoc.toJson}", request)
//            SlackApi.viewPublish(None, slackUserId, None)
//          case Some(other) =>
//            BWLogger.log(getClass.getName, "handleEventCallback", s"app_home_opened($other) IGNORED", request)
//          case None =>
//            BWLogger.log(getClass.getName, "handleEventCallback", "app_home_opened (no 'tab') IGNORED", request)
//        }
//      case (_, _, "message", Some("message_changed")) =>
//        BWLogger.log(getClass.getName, "handleEventCallback", "'message_changed' NOT-IMPLEMENTED", request)
//      case (_, _, "message", Some("message_deleted")) =>
//        BWLogger.log(getClass.getName, "handleEventCallback", "'message_deleted' NOT-IMPLEMENTED", request)
//      case (_, _, "message", Some("bot_message")) =>
//        BWLogger.log(getClass.getName, "handleEventCallback", "'bot_message' NOT-IMPLEMENTED", request)
//      case _ =>
//        SlackApi.sendToChannel(Left(s"Received bad message - Not handled"), channel, None, Some(request))
//        BWLogger.log(getClass.getName, "handleEventCallback", s"ERROR (bad message): ${postData.asDoc.toJson}")
//    }
//  }
//
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "Early-ENTRY", request)
    val postDataStream = getStreamData(request)
    try {
      val postData: DynDoc = Document.parse(postDataStream)
      if (postData.has("challenge")) {
        val challenge = postData.challenge[String]
        response.getWriter.print(challenge)
        response.setContentType("text/plain")
        BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK (handled challenge)")
      } else if (postData.has("event")) {
        val event: DynDoc = postData.event[Document]
        val optType: Option[String] = event.get[String]("type")
        val optSlackUser: Option[String] = event.get[String]("user")
        val optBwUser: Option[DynDoc] = optSlackUser.flatMap(SlackApi.userBySlackId)
        val optSubType: Option[String] = event.get[String]("subtype")
        (optType, optSubType, optBwUser) match {
          case (Some("event_callback"), _, Some(user)) =>
            request.getSession.setAttribute("bw-user", user.asDoc)
            val bodyJson = event.asDoc.toJson
            val response = SlackApi.invokeSlackHandler(user._id[ObjectId].toString, Map("type" -> "event_callback"),
                bodyJson)
            if (response.ok[Int] == 1) {
              //val retVal = response.payload[Document].toJson
              //ToDo: add retVal processing here
              //BWLogger.log(getClass.getName, request.getMethod, s"invokeSlackHandler-OK", request)
            } else {
              BWLogger.log(getClass.getName, request.getMethod, s"invokeSlackHandler-ERROR (${response.asDoc.toJson})",
                  request)
            }
          case (Some("message"), _, Some(user)) =>
            request.getSession.setAttribute("bw-user", user.asDoc)
            val bodyJson = event.asDoc.toJson
            val response = SlackApi.invokeSlackHandler(user._id[ObjectId].toString, Map("type" -> "message"), bodyJson)
            if (response.ok[Int] == 1) {
              //val retVal = response.payload[Document].toJson
              //ToDo: add retVal processing here
              //BWLogger.log(getClass.getName, request.getMethod, s"invokeSlackHandler-OK", request)
            } else {
              BWLogger.log(getClass.getName, request.getMethod, s"invokeSlackHandler-ERROR (${response.asDoc.toJson})",
                  request)
            }
          case (Some("app_home_opened"), _, Some(user)) =>
            request.getSession.setAttribute("bw-user", user.asDoc)
            val tab = event.tab[String]
            val body = new Document("tab", event.tab[String])
            if (tab == "home") {
              val view: DynDoc = event.view[Document]
              body.append("blocks", view.blocks[Many[Document]])
            }
            val urlParams = Map("type" -> "app_home_opened", "tab" -> tab)
            val response = SlackApi.invokeSlackHandler(user._id[ObjectId].toString, urlParams, body.toJson)
            if (response.ok[Int] == 1) {
              val retVal = response.payload[Document].toJson
              if (tab == "home") {
                SlackApi.viewPublish(retVal, optSlackUser.get, None)
              } else if (tab == "messages") {
                //ToDo: add required actions here
              }
              //BWLogger.log(getClass.getName, request.getMethod, s"invokeSlackHandler-OK", request)
            } else {
              BWLogger.log(getClass.getName, request.getMethod, s"invokeSlackHandler-ERROR (${response.asDoc.toJson})",
                  request)
            }
          case _ =>
            BWLogger.log(getClass.getName, request.getMethod, s"EXIT-ERROR Unknown message: $postDataStream", request)
        }
        //BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
      } else {
        BWLogger.log(getClass.getName, request.getMethod, s"EXIT-ERROR Unknown message: $postDataStream", request)
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
