package com.buildwhiz.api

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.BWLogger

class Organization extends HttpServlet with RestUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestGet(request, response, "Organization", "organizations")
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val org: DynDoc = handleRestPost(request, response, "organizations")
    BWLogger.audit(getClass.getName, "handlePost", s"""Added organization '${org.name[String]}'""", request)
  }

  override def doPut(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestGet(request, response, "Organization", "organizations")
  }

  override def doDelete(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestGet(request, response, "Organization", "organizations")
  }

}