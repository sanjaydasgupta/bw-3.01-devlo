package com.buildwhiz.etc

import javax.servlet.http.{Cookie, HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.api.RestUtils
import BWMongoDB3._
import DynDoc._
import com.buildwhiz.utils.BWLogger
import org.bson.Document

class Environment extends HttpServlet with RestUtils {

  private def doCookies(request: HttpServletRequest, response: HttpServletResponse): Unit = {
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
    if (BWMongoDB3.collectionNames.contains("instance_info")) {
      val info: DynDoc = BWMongoDB3.instance_info.find().head
      environment.put("instance", info.instance[String])
    } else {
      environment.put("instance", "???")
    }
    response.getWriter.println(bson2json(environment))
    response.setContentType("application/json")
  }

  private def reportErrors(errorMinutes: String, response: HttpServletResponse): Unit = {
    try {
      val since = System.currentTimeMillis - (errorMinutes.toLong * 60 * 1000)
      val logInfo: Seq[DynDoc] = BWMongoDB3.trace_log.find(Map("milliseconds" -> Map("$gte" -> since)))
      val totalCount = logInfo.length
      val errorCount = logInfo.count(_.event_name[String].contains("ERROR"))
      response.getWriter.println(s"""{"total": $totalCount, "errors": $errorCount, "error_minutes": $errorMinutes}""")
    } catch {
      case _: Throwable =>
        response.getWriter.println(s"""{"total": -1, "errors": -1, "error_minutes": $errorMinutes}""")
    }
    response.setContentType("application/json")
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "handleGet", s"ENTRY", request)

    val parameterMap = getParameterMap(request)
    if (parameterMap.contains("error_minutes")) {
      reportErrors(parameterMap("error_minutes"), response)
    } else {
      doCookies(request, response)
    }
    BWLogger.log(getClass.getName, "handleGet", s"EXIT-OK", request)
  }

}