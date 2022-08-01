package com.buildwhiz.etc

import com.buildwhiz.api.RestUtils
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class Status extends HttpServlet with RestUtils {

  private def sendStatus(errorMinutes: String, response: HttpServletResponse): Unit = {
    try {
      val since = System.currentTimeMillis - (errorMinutes.toLong * 60 * 1000)
      val logInfo: Seq[DynDoc] = BWMongoDB3.trace_log.find(Map("milliseconds" -> Map("$gte" -> since)))
      val totalCount = logInfo.length
      val errorCount = logInfo.count(_.event_name[String].contains("ERROR"))
      response.getWriter.println(s"""{"total": $totalCount, "bad": $errorCount, "minutes": $errorMinutes}""")
    } catch {
      case _: Throwable =>
        response.getWriter.println(s"""{"total": -1, "errors": -1, "error_minutes": $errorMinutes}""")
    }
    response.setContentType("application/json")
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    //BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)

    val parameterMap = getParameterMap(request)
    sendStatus(parameterMap.getOrElse("minutes", "15"), response)
    //BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
  }

}