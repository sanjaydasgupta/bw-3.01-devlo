package com.buildwhiz.api

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class DocumentCategory extends HttpServlet with RestUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestGet(request, response, "DocumentCategory", "document_category_master")
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestPost(request, response, "document_category_master")
  }

}