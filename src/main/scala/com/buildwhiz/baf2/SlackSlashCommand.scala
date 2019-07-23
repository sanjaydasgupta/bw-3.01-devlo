package com.buildwhiz.baf2

import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class SlackSlashCommand extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val userId = parameters("user_id")
      val responseUrl = parameters("response_url")
      val text = parameters("text")
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
