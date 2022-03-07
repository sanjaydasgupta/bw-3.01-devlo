package com.buildwhiz.etc

import javax.servlet.http.{Cookie, HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.api.RestUtils
import BWMongoDB3._
import DynDoc._
import com.buildwhiz.utils.BWLogger
import org.bson.Document

class Environment extends HttpServlet with RestUtils {

  private def doCookies(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val cookies = {val cookies = request.getCookies; if (cookies == null) Array.empty[Cookie] else cookies}
    val email = cookies.find(_.getName == "UserNameEmail") match {
      case Some(c) => c.getValue
      case None => "info@buildwhiz.com"
    }
    val tzRawOffset = java.util.TimeZone.getDefault.getRawOffset
    val environment = new Document("timezone_raw_offset", tzRawOffset)
    environment.put("email", email)
    getUser(request) match {
      case null =>
      case d: Document => environment.put("user", d)
    }
    if (BWMongoDB3.collectionNames.contains("instance_info")) {
      val info: DynDoc = BWMongoDB3.instance_info.find().head
      environment.put("instance", info.instance[String])
    } else {
      environment.put("instance", "???")
    }
    response.getWriter.println(bson2json(environment))
    response.setContentType("application/json")
  }

  private def reportErrors(errorMinutes: String, response: HttpServletResponse): Unit = {
    try {
      val since = System.currentTimeMillis - (errorMinutes.toLong * 60 * 1000)
      val logInfo: Seq[DynDoc] = BWMongoDB3.trace_log.find(Map("milliseconds" -> Map("$gte" -> since)))
      val totalCount = logInfo.length
      val errorCount = logInfo.count(_.event_name[String].contains("ERROR"))
      response.getWriter.println(s"""{"total": $totalCount, "bad": $errorCount, "minutes": $errorMinutes}""")
    } catch {
      case _: Throwable =>
        response.getWriter.println(s"""{"total": -1, "errors": -1, "error_minutes": $errorMinutes}""")
    }
    response.setContentType("application/json")
  }

  private def reportData(envVariable: String, response: HttpServletResponse): Unit = {
    response.setContentType("text/html")
    val writer = response.getWriter
    case class FldSpec(name: String, asString: Any => String)
    def phoneFormatter(rec: Any): String = {
      rec.asInstanceOf[Many[Document]].filter(_.phone[String].nonEmpty).
        map(em => s"${em.phone[String]} (${em.`type`[String]})").mkString(",")
    }
    def emailFormatter(rec: Any): String = {
      rec.asInstanceOf[Many[Document]].filter(_.email[String].nonEmpty).
        map(em => s"${em.email[String]} (${em.`type`[String]})").mkString(",")
    }
    def booleanFormatter(field: Any): String = {
      if (field == null) "false" else field.toString
    }
    def primitiveFormatter(field: Any): String = {
      if (field == null) "" else field.toString
    }
    def csvFormatter(field: Any): String = {
      if (field == null) "" else field.asInstanceOf[Many[_]].mkString(",")
    }
    def personData(): Unit = {
      val fields = Seq[FldSpec](FldSpec("_id", primitiveFormatter), FldSpec("first_name", primitiveFormatter),
        FldSpec("last_name", primitiveFormatter), FldSpec("organization_id", primitiveFormatter),
        FldSpec("enabled", booleanFormatter), FldSpec("emails", emailFormatter), FldSpec("phones", phoneFormatter),
        FldSpec("rating", primitiveFormatter), FldSpec("skills", csvFormatter),
        FldSpec("years_experience", primitiveFormatter), FldSpec("slack_id", primitiveFormatter))
      writer.println(fields.map(_.name).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      val orgs: Seq[DynDoc] = BWMongoDB3.persons.find()
      for (org <- orgs) {
        val tds = fields.map(f => (f.name, f.asString(org.asDoc.get(f.name))))
        writer.println(tds.map(td => td._2).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      }
    }
    def orgData(): Unit = {
      val fields = Seq[FldSpec](FldSpec("_id", primitiveFormatter), FldSpec("name", primitiveFormatter),
        FldSpec("active", booleanFormatter), FldSpec("areas_of_operation", csvFormatter),
        FldSpec("rating", primitiveFormatter), FldSpec("skills", csvFormatter),
        FldSpec("years_experience", primitiveFormatter), FldSpec("design_partner", booleanFormatter),
        FldSpec("project_sponsor", booleanFormatter), FldSpec("trade_partner", booleanFormatter))
      writer.println(fields.map(_.name).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      val orgs: Seq[DynDoc] = BWMongoDB3.organizations.find()
      for (org <- orgs) {
        val tds = fields.map(f => (f.name, f.asString(org.asDoc.getOrElse(f.name, null))))
        writer.println(tds.map(td => td._2).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      }
    }
    def teamData(): Unit = {
      val fields = Seq[FldSpec](FldSpec("_id", primitiveFormatter), FldSpec("team_name", primitiveFormatter),
        FldSpec("group", primitiveFormatter), FldSpec("skill", csvFormatter),
        FldSpec("organization_id", primitiveFormatter), FldSpec("project_id", primitiveFormatter))
      writer.println(fields.map(_.name).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      val orgs: Seq[DynDoc] = BWMongoDB3.teams.find()
      for (org <- orgs) {
        val tds = fields.map(f => (f.name, f.asString(org.asDoc.getOrElse(f.name, null))))
        writer.println(tds.map(td => td._2).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      }
    }
    writer.println("<html><body>")
    writer.println("<h2>Organizations</h2>")
    writer.println("""<table id="organizations" border="1">""")
    orgData()
    writer.println("</table>")
    writer.println("<h2>Persons</h2>")
    writer.println("""<table id="persons" border="1">""")
    personData()
    writer.println("</table>")
    writer.println("<h2>Teams</h2>")
    writer.println("""<table id="teams" border="1">""")
    teamData()
    writer.println("</table>")
    writer.println("</body></html>")
  }

  private def reportEnvironment(envVariable: String, response: HttpServletResponse): Unit = {
    sys.env.get(envVariable) match {
      case Some(envValue) =>
        response.getWriter.println(s"""{"variable": "$envVariable", "value": "$envValue", "error": false""")
      case None =>
        response.getWriter.println(s"""{"variable": "$envVariable", "value": "", "error": true""")
    }
    response.setContentType("application/json")
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)

    val parameterMap = getParameterMap(request)
    if (parameterMap.contains("minutes")) {
      reportErrors(parameterMap("minutes"), response)
    } else if (parameterMap.contains("environment")) {
        reportEnvironment(parameterMap("environment"), response)
    } else if (parameterMap.contains("data")) {
      reportData(parameterMap("data"), response)
    } else {
      doCookies(request, response)
    }
    BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
  }

}