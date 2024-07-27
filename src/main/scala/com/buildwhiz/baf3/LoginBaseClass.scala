package com.buildwhiz.baf3

import com.buildwhiz.Entry
import com.buildwhiz.baf2.{PersonApi, PhaseApi, ProjectApi}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import java.io.{File => javaFile}
import javax.servlet.http.{Cookie, HttpServlet, HttpServletRequest, HttpServletResponse}

abstract class LoginBaseClass extends HttpServlet with HttpUtils with DateTimeUtils {

  protected def cookieSessionSet(userNameEmail: String, person: Document, hostName: String, request: HttpServletRequest,
        response: HttpServletResponse): Unit = {
    val session = request.getSession
    Entry.sessionCache.put(session.getId, session)
    val siteConfigInfos: Seq[DynDoc] = BWMongoDB3.global_configs.find(Map("site_name" -> hostName,
      "control_name" -> "landing-page-and-menu-definition"))
    BWLogger.log(getClass.getName, request.getMethod,
      s"INFO cookieSessionSet(): Stored ${siteConfigInfos.length} global_configs records", request)
    session.setAttribute("siteConfigInfos", siteConfigInfos)
    session.setAttribute("bw-user", person)
    session.setMaxInactiveInterval(0)
    session.setAttribute("host-name", hostName)
    val cookie = new Cookie("UserNameEmail", userNameEmail)
    cookie.setHttpOnly(false)
    cookie.setMaxAge(30 * 24 * 60 * 60)
    response.addCookie(cookie)
  }

  protected def recordLoginTime(person: DynDoc): Unit = {
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

  protected def defaultPhase(projectOid: ObjectId, personRecord: DynDoc): Option[DynDoc] = {
    val candidatePhases = ProjectApi.allPhases(projectOid).filter(PhaseApi.hasRole(personRecord._id[ObjectId], _))
    candidatePhases.find(PhaseApi.isActive(_, BWMongoDB3)) match {
      case anActivePhase: Some[DynDoc] => anActivePhase
      case None => candidatePhases.headOption
    }
  }

  def validateIdToken(idTokenString: String, optEmail: Option[String] = None): (Boolean, String, DynDoc => Boolean)

  private def lastModified2(f: javaFile): Long = {
    if (f.isDirectory) {
      val files = f.listFiles()
      if (files.isEmpty) {
        f.lastModified()
      } else {
        files.map(lastModified2).max
      }
    } else {
      f.lastModified()
    }
  }

  private def dates(request: HttpServletRequest): Map[String, String] = {
    val allUnknown = Map("date_java" -> "Unknown", "date_node" -> "Unknown", "date_ui" -> "Unknown",
      "date_java2" -> "Unknown", "date_node2" -> "Unknown", "date_ui2" -> "Unknown")
    new javaFile("server").listFiles.find(_.getName.startsWith("apache-tomcat-")) match {
      case Some(tomcatDirectory) =>
        tomcatDirectory.listFiles.find(_.getName.startsWith("webapps")) match {
          case Some(webapps) =>
            val user: DynDoc = getUser(request)
            val tz = user.tz[String]
            val (javaDate, javaDate2) = webapps.listFiles.find(f => f.getName == "bw-3.01" && f.isDirectory) match {
              case Some(javaWarDir) =>
                val maxTime = javaWarDir.listFiles.map(_.lastModified).max
                (dateString(maxTime, tz), dateString2(maxTime, tz))
              case None => ("Unknown", "Unknown")
            }
            val (nodeDate, nodeDate2) = Seq(new javaFile("/home/ubuntu/node/src")).find(f => f.exists && f.isDirectory) match {
              case Some(nodeDir) =>
                val maxTime = lastModified2(nodeDir)
                (dateString(maxTime, tz), dateString2(maxTime, tz))
              case None => ("Unknown", "Unknown")
            }
            val (uiDate, uiDate2) = webapps.listFiles.find(f => f.getName == "vv" && f.isDirectory) match {
              case Some(uiDir) =>
                val maxTime = uiDir.listFiles.map(_.lastModified).max
                (dateString(maxTime, tz), dateString2(maxTime, tz))
              case None => ("Unknown", "Unknown")
            }
            Map("date_java" -> javaDate, "date_node" -> nodeDate, "date_ui" -> uiDate,
                "date_java2" -> javaDate2, "date_node2" -> nodeDate2, "date_ui2" -> uiDate2)
          case None => allUnknown
        }
      case None => allUnknown
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
//      BWLogger.log(getClass.getName, request.getMethod, s"SESSIONID: $sessionId, COOKIES: $cookies", request, isLogin = true)
      val postData = getStreamData(request)
      //BWLogger.log(getClass.getName, request.getMethod, s"POST-data: '$postData'", request, isLogin = true)
      val parameters: DynDoc = if (postData.nonEmpty) Document.parse(postData) else new Document()
      if (parameters.has("idtoken")) {
        val (idTokenOk, email, validator) = validateIdToken(parameters.idtoken[String], parameters.get[String]("email"))
        if (idTokenOk) {
          val person: Option[Document] = BWMongoDB3.persons.find(Map("enabled" -> true,
              "emails" -> Map($elemMatch -> Map("type" -> "work", "email" -> email)))).headOption.map(_.asDoc)
          val result = person match {
            case Some(personRecord) if validator(personRecord) =>
              val hostName = getHostName(request)
              cookieSessionSet(email, personRecord, hostName, request, response)
              val personIsAdmin = PersonApi.isBuildWhizAdmin(Right(personRecord))
              personRecord.put("menu_items", displayedMenuItems(personIsAdmin, request, starting = true))
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
                "selected_project_id", "selected_phase_id", "single_project_indicator").
                filter(f => personRecord.containsKey(f))
              val roles = if (personIsAdmin) Seq("BW-Admin") else Seq("NA")
              val landingInfo = landingPageInfo(personRecord, request)
              val resultPerson = new Document(resultFields.map(f => (f, personRecord.get(f))).toMap ++
                  Map("roles" -> roles, "JSESSIONID" -> request.getSession.getId, "master_data" -> masterData) ++
                  Map("landing_page" -> landingInfo) ++ dates(request) ++ Map("email" -> email))
              if (!resultPerson.containsKey("dummies"))
                resultPerson.append("dummies", false)
              recordLoginTime(personRecord)
              val message = s"Login OK ($email). Landing-Page: $landingInfo"
              BWLogger.audit(getClass.getName, request.getMethod, message, request)
              bson2json(resultPerson)
            case _ =>
              BWLogger.log(getClass.getName, request.getMethod, s"EXIT-ERROR unknown work-email: $email", request)
              """{"_id": "", "first_name": "", "last_name": ""}"""
          }
          response.getWriter.print(result)
        } else {
          BWLogger.log(getClass.getName, request.getMethod, "EXIT-ERROR Id-Token-Verifier failure", request)
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
//        BWLogger.log(getClass.getName, request.getMethod, s"Logout ($workEmail)", request)
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
