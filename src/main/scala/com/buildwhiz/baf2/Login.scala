package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, CryptoUtils, HttpUtils}
import javax.servlet.http.{Cookie, HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class Login extends HttpServlet with HttpUtils with CryptoUtils {

  private def cookieSessionSet(userNameEmail: String, person: Document, request: HttpServletRequest,
        response: HttpServletResponse): Unit = {
    request.getSession.setAttribute("bw-user", person)
    request.getSession.setMaxInactiveInterval(0)
    val cookie = new Cookie("UserNameEmail", userNameEmail)
    cookie.setHttpOnly(true)
    cookie.setMaxAge(30 * 24 * 60 * 60)
    response.addCookie(cookie)
  }

  private def recordLoginTime(person: DynDoc): Unit = {
    if (person.has("timestamps")) {
      val timestamps: DynDoc = person.timestamps[Document]
      if (timestamps.has("first_login")) {
        BWMongoDB3.persons.updateOne(Map("_id" -> person._id[ObjectId]),
          Map("$set" -> Map("timestamps.last_login" -> System.currentTimeMillis)))
      } else {
        BWMongoDB3.persons.updateOne(Map("_id" -> person._id[ObjectId]),
          Map("$set" -> Map("timestamps.first_login" -> System.currentTimeMillis)))
      }
    } else {
      BWMongoDB3.persons.updateOne(Map("_id" -> person._id[ObjectId]),
        Map("$set" -> Map("timestamps" -> Map("first_login" -> System.currentTimeMillis))))
    }
  }

  /*

    curl -H "Content-Type: application/json" -X POST
    -d '{"email":"sanjay.dasgupta@buildwhiz.com","password":"Sanjay"}'
    http://54.82.1.60:8080/bw-responsive-1.01/etc/LoginPost

  */

  private def addMenuItems(person: DynDoc): Unit = {
    val menuItems: Seq[Document] = Seq(
      Map("name" -> "Documents", "url" -> "/documents", "icon" -> "documents"),
      Map("name" -> "RFIs", "url" -> "/rfis", "icon" -> "rfis"),
      //Map("name" -> "Projects", "url" -> "/projects", "icon" -> "projects"),
      Map("name" -> "Tasks", "url" -> "/task-list", "icon" -> "tasks"),
      Map("name" -> "Profile", "url" -> "/profile", "icon" -> "profile"),
      Map("name" -> "Help", "url" -> "/help", "icon" -> "help")
    )
    person.asDoc.put("menu_items", /*documentMenu ++ */menuItems)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request, isLogin = true)
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
          case None =>
            BWLogger.log(getClass.getName, "doPost", s"Login ERROR: $email", request)
            """{"_id": "", "first_name": "", "last_name": ""}"""
          case Some(personRecord) =>
            cookieSessionSet(email, personRecord, request, response)
            addMenuItems(personRecord)
            if (!personRecord.containsKey("document_filter_labels"))
              personRecord.put("document_filter_labels", Seq.empty[String])
            val resultFields = Seq("_id", "first_name", "last_name", "roles", "organization_id"/*, "project_ids"*/,
              "tz", "email_enabled", "ui_hidden", "document_filter_labels", "menu_items").filter(f => personRecord.containsKey(f))
            val resultPerson = new Document(resultFields.map(f => (f, personRecord.get(f))).toMap)
            recordLoginTime(personRecord)
            BWLogger.audit(getClass.getName, "doPost", "Login OK", request)
            bson2json(resultPerson)
        }
        response.getWriter.print(result)
        response.setContentType("application/json")
        response.setStatus(HttpServletResponse.SC_OK)
      } else if (request.getSession.getAttribute("bw-user") != null) {
        val user: DynDoc = getUser(request)
        val emails: Seq[DynDoc] = user.emails[Many[Document]]
        val workEmail: String = emails.find(_.`type`[String] == "work").head.email[String]
        request.getSession.removeAttribute("bw-user")
        request.getSession.invalidate()
        BWLogger.log(getClass.getName, "doPost", s"Logout ($workEmail)", request)
      } else {
        val result = """{"_id": "", "first_name": "", "last_name": ""}"""
        response.getWriter.print(result)
        response.setContentType("application/json")
        response.setStatus(HttpServletResponse.SC_OK)
        BWLogger.log(getClass.getName, "doPost", s"EXIT-OK Login without parameters ($result)", request)
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", parameters.toSeq: _*)
        //t.printStackTrace()
        throw t
    }
  }
}
