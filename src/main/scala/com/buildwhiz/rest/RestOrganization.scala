package com.buildwhiz.rest

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.Utils

class RestOrganization extends HttpServlet with Utils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestGet(request, response, "organization", "organizations")
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestPost(request, response, "organization")
  }

  override def doPut(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestGet(request, response, "organization", "organizations")
  }

  override def doDelete(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestGet(request, response, "organization", "organizations")
  }

}