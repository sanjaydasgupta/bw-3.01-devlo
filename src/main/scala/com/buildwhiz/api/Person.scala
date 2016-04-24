package com.buildwhiz.api

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3.DynDoc
import org.bson.Document

class Person extends HttpServlet with RestUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestPost(request, response, "persons")
  }

  override def doPut(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestPut(request, response, "Person", "persons")
  }

  override def doDelete(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestDelete(request, response, "Person", "persons")
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    def personSorter(a: Document, b: Document): Boolean = {
      def name(d: DynDoc): String = s"${d.first_name[String]} ${d.last_name[String]}"
      name(a) < name(b)
    }

    handleRestGet(request, response, "Person", "persons", Set("password"), Some(personSorter))
  }

}