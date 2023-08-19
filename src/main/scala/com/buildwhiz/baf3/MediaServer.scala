package com.buildwhiz.baf3

import com.buildwhiz.utils.HttpUtils

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

object MediaServer extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    response.setContentType("text/html")
    val writer = response.getWriter
    writer.println("""<html><table width="100%"><tr width="100%"><td>NOT  YET  IMPLEMENTED</td></tr></table></html>""")
  }
}
