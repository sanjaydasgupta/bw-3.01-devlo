package com.buildwhiz.slack

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class SlackSlashCommand extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  private def userBySlackId(slackUserId: String): Option[DynDoc] = {
    BWMongoDB3.persons.find(Map("slack_id" -> slackUserId)).headOption
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val teamId = parameters("team_id")
      //val enterpriseId = parameters("enterprise_id")
      val slackUserId: String = parameters("user_id")
      val slackUserName: String = parameters("user_name")
      val channelId = parameters("channel_id")
      val channelName = parameters("channel_name")
      val triggerId = parameters("trigger_id")
      val text = parameters("text")
      userBySlackId(slackUserId) match {
        case Some(user: DynDoc) =>
          val userName = PersonApi.fullName(user)
          response.getWriter.print(s"""{"text": "Hi $slackUserName ($userName),""" +
            """ your message is being processed"}""")
        case None =>
          response.getWriter.print(s"""{"text": "Hi $slackUserName, your Slack-Id is not registered""" +
            """ and your message was ignored"}""")
      }
      response.setContentType("application/json")
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
