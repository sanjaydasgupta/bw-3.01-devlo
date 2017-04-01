package com.buildwhiz.etc

import javax.servlet.http.{Cookie, HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, CryptoUtils, HttpUtils}
import org.bson.Document

import scala.collection.JavaConverters._

class LoginPost extends HttpServlet with HttpUtils with CryptoUtils {

  private def cookieSessionSet(userNameEmail: String, person: Document, request: HttpServletRequest,
        response: HttpServletResponse): Unit = {
    request.getSession.setAttribute("bw-user", person)
    request.getSession.setMaxInactiveInterval(0)
    val cookie = new Cookie("UserNameEmail", userNameEmail)
    cookie.setHttpOnly(true)
    cookie.setMaxAge(30 * 24 * 60 * 60)
    response.addCookie(cookie)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    parameters("X-FORWARDED-FOR") = request.getHeader("X-FORWARDED-FOR")
    parameters("User-Agent") = request.getHeader("User-Agent")
    BWLogger.log(getClass.getName, "doPost", "ENTRY", parameters.toSeq: _*)
    try {
      val postData = getStreamData(request)
      val loginParameters: DynDoc = if (postData.nonEmpty) Document.parse(postData) else new Document()
      if (loginParameters.has("email") && loginParameters.has("password")) {
        val email = loginParameters.email[String]
        val password = loginParameters.password[String]
        val query = Map("emails" -> Map("type" -> "work", "email" -> email), "password" -> md5(password),
          "enabled" -> true)
        val person: Option[Document] = BWMongoDB3.persons.find(query).asScala.headOption
        val result = person match {
          case None => """{"_id": "", "first_name": "", "last_name": ""}"""
          case Some(p) =>
            cookieSessionSet(email, p, request, response)
            val resultFields = Seq("_id", "first_name", "last_name", "roles", "organization_id", "project_ids",
              "tz", "email_enabled").filter(f => p.containsKey(f))
            val resultPerson = new Document(resultFields.map(f => (f, p.get(f))).toMap)
            bson2json(resultPerson)
        }
        response.getWriter.print(result)
        response.setContentType("application/json")
        response.setStatus(HttpServletResponse.SC_OK)
        BWLogger.log(getClass.getName, "doPost", s"EXIT-OK Log IN ($result)", parameters.toSeq: _*)
      } else if (request.getSession.getAttribute("bw-user") != null) {
        request.getSession.removeAttribute("bw-user")
        request.getSession.invalidate()
        BWLogger.log(getClass.getName, "doPost", s"EXIT-OK Log OUT", parameters.toSeq: _*)
      } else {
        val result = """{"_id": "", "first_name": "", "last_name": ""}"""
        response.getWriter.print(result)
        response.setContentType("application/json")
        response.setStatus(HttpServletResponse.SC_OK)
        BWLogger.log(getClass.getName, "doPost", s"EXIT-OK Log IN ($result)", parameters.toSeq: _*)
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", parameters.toSeq: _*)
        t.printStackTrace()
        throw t
    }
  }
}
