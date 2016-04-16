package com.buildwhiz.baf

import java.security.MessageDigest
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{BWLogger, BWMongoDB3, Utils}
import org.bson.Document
import BWMongoDB3._

import scala.collection.JavaConversions._

class LoginPost extends HttpServlet with Utils {

  private def md5(password: String): String = {
    val messageDigest = MessageDigest.getInstance("MD5")
    messageDigest.update(password.getBytes(), 0, password.length())
    val bytes = messageDigest.digest()
    val hexValues = bytes.map(b => "%02x".format(b))
    hexValues.mkString
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
      val person: Option[Document] = BWMongoDB3.persons.find(query).headOption
      val result = person match {
        case None => """{"_id": "", "first_name": "", "last_name": ""}"""
        case Some(p) =>
          val permittedFields = Set("_id", "first_name", "last_name", "omniclass34roles",
            "organization_id", "project_ids")
          val resultPerson = new Document()
          p.keySet.foreach(key => if (permittedFields.contains(key)) resultPerson(key) = p(key))
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
