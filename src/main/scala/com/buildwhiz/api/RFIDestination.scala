package com.buildwhiz.api

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3.DynDoc
import org.bson.Document

class RFIDestination extends HttpServlet with RestUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestPost(request, response, "rfi_destinations")
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    handleRestGet(request, response, "RFIDestination", "rfi_destinations", Set.empty[String], None)
  }

}