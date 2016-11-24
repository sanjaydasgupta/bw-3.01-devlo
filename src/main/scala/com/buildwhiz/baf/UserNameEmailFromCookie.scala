package com.buildwhiz.baf

import javax.servlet.http.{Cookie, HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.HttpUtils
import com.buildwhiz.infra.BWLogger

class UserNameEmailFromCookie extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val cookies = {val c = request.getCookies; if (c == null) Array.empty[Cookie] else c}
      val email = cookies.find(_.getName == "UserNameEmail") match {
        case Some(c) => c.getValue
        case None => "xyz@buildwhiz.com"
      }
      response.getOutputStream.write(email.getBytes)
      response.setContentType("text/plain")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", s"EXIT-OK (UserNameEmail='$email')", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
