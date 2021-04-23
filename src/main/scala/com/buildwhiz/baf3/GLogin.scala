package com.buildwhiz.baf3

import com.buildwhiz.Entry

import java.util.Collections
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}

import javax.servlet.http.{Cookie, HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId
import com.buildwhiz.baf2.{PersonApi, PhaseApi, ProjectApi}
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory

import java.io.{File => javaFile}

class GLogin extends HttpServlet with HttpUtils with DateTimeUtils {

  private def cookieSessionSet(userNameEmail: String, person: Document, request: HttpServletRequest,
        response: HttpServletResponse): Unit = {
    val session = request.getSession
    Entry.sessionCache.put(session.getId, session)
    session.setAttribute("bw-user", person)
    session.setMaxInactiveInterval(0)
    val cookie = new Cookie("UserNameEmail", userNameEmail)
    cookie.setHttpOnly(false)
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

  private def defaultPhase(projectOid: ObjectId, personRecord: DynDoc): Option[DynDoc] = {
    val candidatePhases = ProjectApi.allPhases(projectOid).filter(PhaseApi.hasRole(personRecord._id[ObjectId], _))
    candidatePhases.find(PhaseApi.isActive) match {
      case anActivePhase: Some[DynDoc] => anActivePhase
      case None => candidatePhases.headOption
    }
  }

  private def validateIdToken(idTokenString: String, emailParameter: String): Boolean = {
    val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    val jsonFactory = JacksonFactory.getDefaultInstance
    val clientId = "318594985671-gfojh2kiendld330k65eajmjdifudpct.apps.googleusercontent.com"
    val verifier = new GoogleIdTokenVerifier.Builder(httpTransport, jsonFactory).
        setAudience(Collections.singletonList(clientId)).build()
    val idToken = verifier.verify(idTokenString)
    if (idToken != null) {
      val payload = idToken.getPayload
      val email = payload.getEmail
      val emailVerified: Boolean = payload.getEmailVerified
      emailVerified && (email == emailParameter)
    } else {
      false
    }
  }

  private def dates(request: HttpServletRequest): Map[String, String] = {
    new javaFile("server").listFiles.find(_.getName.startsWith("apache-tomcat-")) match {
      case Some(tomcatDirectory) =>
        tomcatDirectory.listFiles.find(_.getName.startsWith("webapps")) match {
          case Some(webapps) =>
            val user: DynDoc = getUser(request)
            val tz = user.tz[String]
            val bwWarDate = webapps.listFiles.find(f => f.getName == "bw-3.01" && f.isDirectory) match {
              case Some(java) => dateString(java.lastModified(), tz)
              case None => "Unknown"
            }
            val nodeDate = Seq(new javaFile("/home/ubuntu/node")).find(f => f.exists && f.isDirectory) match {
              case Some(java) => dateString(java.lastModified(), tz)
              case None => "Unknown"
            }
            val vvDate = webapps.listFiles.find(f => f.getName == "vv" && f.isDirectory) match {
              case Some(vv) => dateString(vv.lastModified(), tz)
              case None => "Unknown"
            }
            Map("date_java" -> bwWarDate, "date_node" -> nodeDate, "date_ui" -> vvDate)
          case None => Map("date_java" -> "Unknown", "date_node" -> "Unknown", "date_ui" -> "Unknown")
        }
      case None => Map("date_java" -> "Unknown", "date_node" -> "Unknown", "date_ui" -> "Unknown")
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    //val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request, isLogin = true)
    try {
//      val sessionId = request.getSession.getId
//      val cookies: String = if (request.getCookies == null) {
//        ""
//      } else {
//        request.getCookies.map(c => s"[name:'${c.getName}' domain:'${c.getDomain}' path:'${c.getPath}' value:'${c.getValue}']").
//            mkString("; ")
//      }
//      BWLogger.log(getClass.getName, "doPost", s"SESSIONID: $sessionId, COOKIES: $cookies", request, isLogin = true)
      val postData = getStreamData(request)
      //BWLogger.log(getClass.getName, "doPost", s"POST-data: '$postData'", request, isLogin = true)
      val parameters: DynDoc = if (postData.nonEmpty) Document.parse(postData) else new Document()
      if (parameters.has("idtoken") && parameters.has("email")) {
        val idToken = parameters.idtoken[String]
        val email = parameters.email[String]
        val idTokenOk = validateIdToken(idToken, email)
        if (idTokenOk) {
          val person: Option[Document] =
            BWMongoDB3.persons.find(Map("emails" -> Map("type" -> "work", "email" -> email))).headOption.map(_.asDoc)
          val result = person match {
            case None =>
              BWLogger.log(getClass.getName, request.getMethod, s"EXIT-ERROR Google-OK but no work-email: $email", request)
              """{"_id": "", "first_name": "", "last_name": ""}"""
            case Some(personRecord) =>
              cookieSessionSet(email, personRecord, request, response)
              val personIsAdmin = PersonApi.isBuildWhizAdmin(Right(personRecord))
              personRecord.put("menu_items", displayedMenuItems(personIsAdmin, starting = true))
              if (!personRecord.containsKey("document_filter_labels"))
                personRecord.put("document_filter_labels", Seq.empty[String])
              if (!personRecord.containsKey("selected_project_id")) {
                personRecord.put("selected_project_id", "")
                personRecord.put("selected_phase_id", "")
              } else if (!personRecord.containsKey("selected_phase_id")) {
                defaultPhase(personRecord.getObjectId("selected_project_id"), personRecord) match {
                  case None => personRecord.put("selected_phase_id", "")
                  case Some(phase) => personRecord.put("selected_phase_id", phase._id[ObjectId])
                }
              }
              val resultFields = Seq("_id", "first_name", "last_name", "organization_id", "dummies",
                "tz", "email_enabled", "ui_hidden", "document_filter_labels", "menu_items", "font_size",
                "selected_project_id", "selected_phase_id").filter(f => personRecord.containsKey(f))
              val roles = if (personIsAdmin) Seq("BW-Admin") else Seq("NA")
              val resultPerson = new Document(resultFields.map(f => (f, personRecord.get(f))).toMap ++
                  Map("roles" -> roles, "JSESSIONID" -> request.getSession.getId, "master_data" -> masterData) ++
                 dates(request))
              if (!resultPerson.containsKey("dummies"))
                resultPerson.append("dummies", false)
              recordLoginTime(personRecord)
              BWLogger.audit(getClass.getName, request.getMethod, "Login GoogleIdTokenVerifier OK", request)
              bson2json(resultPerson)
          }
          response.getWriter.print(result)
        } else {
          BWLogger.log(getClass.getName, request.getMethod, "EXIT-ERROR GoogleIdTokenVerifier failure", request)
          response.getWriter.print("""{"_id": "", "first_name": "", "last_name": ""}""")
        }
        response.setContentType("application/json")
        response.setStatus(HttpServletResponse.SC_OK)
//      } else if (request.getSession.getAttribute("bw-user") != null) {
//        val user: DynDoc = getUser(request)
//        val emails: Seq[DynDoc] = user.emails[Many[Document]]
//        val workEmail: String = emails.find(_.`type`[String] == "work").head.email[String]
//        request.getSession.removeAttribute("bw-user")
//        request.getSession.invalidate()
//        BWLogger.log(getClass.getName, "doPost", s"Logout ($workEmail)", request)
      } else {
        val result = """{"_id": "", "first_name": "", "last_name": ""}"""
        response.getWriter.print(result)
        response.setContentType("application/json")
        response.setStatus(HttpServletResponse.SC_OK)
        BWLogger.log(getClass.getName, request.getMethod, s"EXIT-ERROR Login without parameters ($result)", request)
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
