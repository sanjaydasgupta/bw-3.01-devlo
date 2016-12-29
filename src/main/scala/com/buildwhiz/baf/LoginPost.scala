package com.buildwhiz.baf

import javax.servlet.http.{Cookie, HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.Document
import BWMongoDB3._
import com.buildwhiz.{CryptoUtils, HttpUtils}

import scala.collection.JavaConverters._

class LoginPost extends HttpServlet with HttpUtils with CryptoUtils {

  private def storeCookie(userNameEmail: String, request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val cookie = new Cookie("UserNameEmail", userNameEmail)
    cookie.setMaxAge(30 * 24 * 60 * 60)
    response.addCookie(cookie)
    BWLogger.log(getClass.getName, "storeCookie", "Stored UserName Cookie", request)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    parameters("X-FORWARDED-FOR") = request.getHeader("X-FORWARDED-FOR")
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val email = parameters("email")
      val password = parameters("password")
      //val query = Map("email_work" -> userEmail, "password" -> password)
      //val passwordHash = "%x".format(password.hashCode)
      val query = Map("emails" -> Map("type" -> "work", "email" -> email), "password" -> md5(password))
      val person: Option[Document] = BWMongoDB3.persons.find(query).asScala.headOption
      val result = person match {
        case None => """{"_id": "", "first_name": "", "last_name": ""}"""
        case Some(p) =>
          storeCookie(email, request, response)
          val permittedFields = Set("_id", "first_name", "last_name", "roles",
            "organization_id", "project_ids")
          val resultPerson = new Document()
          p.keySet.asScala.foreach(key => if (permittedFields.contains(key)) resultPerson.asScala(key) = p.asScala(key))
          bson2json(resultPerson)
      }
      response.getWriter.print(result)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doPost", s"EXIT-OK (${person.isDefined})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
