package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class SlackSlashCommand extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val teamId = parameters("team_id")
      //val enterpriseId = parameters("enterprise_id")
      val userId: String = parameters("user_id")
      val channelId = parameters("channel_id")
      val channelName = parameters("channel_name")
      val triggerId = parameters("trigger_id")
      val responseUrl = parameters("response_url")
      val text = parameters("text")
      BWMongoDB3.persons.find(Map("slack_id" -> userId)).headOption match {
        case Some(user: DynDoc) =>
          val userName = s"${user.first_name[String]} ${user.last_name[String]}"
          response.getWriter.print(s"""{"text": "Hi $userName, your message is being processed"}""")
        case None =>
          response.getWriter.print("""{"text": "Unknown user, your message was ignored"}""")
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
