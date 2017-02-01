package com.buildwhiz.etc

import javax.servlet.http.{Cookie, HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.api.RestUtils
import com.buildwhiz.utils.BWLogger
import org.bson.Document

class Environment extends HttpServlet with RestUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "handleGet", s"ENTRY", request)

    val cookies = {val cookies = request.getCookies; if (cookies == null) Array.empty[Cookie] else cookies}
    val email = cookies.find(_.getName == "UserNameEmail") match {
      case Some(c) => c.getValue
      case None => "info@buildwhiz.com"
    }
    val tzRawOffset = java.util.TimeZone.getDefault.getRawOffset
    val environment = new Document("timezone_raw_offset", tzRawOffset)
    environment.put("email", email)
    getUser(request) match {
      case null =>
      case d: Document => environment.put("user", d)
    }
    response.getWriter.println(bson2json(environment))
    response.setContentType("application/json")
    response.setStatus(HttpServletResponse.SC_OK)

    BWLogger.log(getClass.getName, "handleGet", s"EXIT-OK", request)
  }

}