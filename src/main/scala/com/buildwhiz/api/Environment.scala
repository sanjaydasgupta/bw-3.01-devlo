package com.buildwhiz.api

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWLogger

class Environment extends HttpServlet with RestUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "handleGet", s"ENTRY", request)

    val tzRawOffset = java.util.TimeZone.getDefault.getRawOffset
    val environment = s"""{"timezone_raw_offset": $tzRawOffset}"""
    response.getWriter.println(environment)
    response.setContentType("application/json")
    response.setStatus(HttpServletResponse.SC_OK)

    BWLogger.log(getClass.getName, "handleGet", s"EXIT-OK", request)
  }

}