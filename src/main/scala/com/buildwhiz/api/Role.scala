package com.buildwhiz.api

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3.DynDoc
import org.bson.Document

class Role extends HttpServlet with RestUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    def roleSorter = (a: Document, b: Document) => {
      val a1: DynDoc = a
      val b1: DynDoc = b
      val first = s"${a1.category[String]}/${a1.name[String]}".toLowerCase
      val second = s"${b1.category[String]}/${b1.name[String]}".toLowerCase
      first < second
    }
    handleRestGet(request, response, "Role", "roles_master", sorter=Some(roleSorter))
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestPost(request, response, "roles_master")
  }

}