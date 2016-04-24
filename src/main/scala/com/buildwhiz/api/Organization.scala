package com.buildwhiz.api

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class Organization extends HttpServlet with RestUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestGet(request, response, "Organization", "organizations")
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestPost(request, response, "Organization")
  }

  override def doPut(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestGet(request, response, "Organization", "organizations")
  }

  override def doDelete(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestGet(request, response, "Organization", "organizations")
  }

}