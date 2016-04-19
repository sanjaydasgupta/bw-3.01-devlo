package com.buildwhiz.rest

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.Utils

class Organization extends HttpServlet with Utils {

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