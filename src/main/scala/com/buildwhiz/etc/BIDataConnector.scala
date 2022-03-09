package com.buildwhiz.etc

import com.buildwhiz.api.RestUtils
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.BWLogger
import org.bson.Document
import org.bson.types.ObjectId

import java.io.PrintWriter
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class BIDataConnector extends HttpServlet with RestUtils {

  def phoneFormatter(rec: Any): String = {
    rec.asInstanceOf[Many[Document]].filter(_.phone[String].nonEmpty).
      map(em => s"${em.phone[String]} (${em.`type`[String]})").mkString(",")
  }

  private def emailFormatter(rec: Any): String = {
    rec.asInstanceOf[Many[Document]].filter(_.email[String].nonEmpty).
      map(em => s"${em.email[String]} (${em.`type`[String]})").mkString(",")
  }

  private def booleanFormatter(field: Any): String = {
    if (field == null) "false" else field.toString
  }

  private def primitiveFormatter(field: Any): String = {
    if (field == null) "" else field.toString
  }

  private def csvFormatter(field: Any): String = {
    if (field == null) "" else field.asInstanceOf[Many[_]].mkString(",")
  }

  case class FldSpec(name: String, asString: Any => String)

  def personData(writer: PrintWriter): Unit = {
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

  def orgData(writer: PrintWriter): Unit = {
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

  def teamData(writer: PrintWriter): Unit = {
    def membersFormatter(members: Any): String = {
      if (members == null) {
        ""
      } else {
        members.asInstanceOf[Many[Document]].map(m =>
          s"""${m.person_id[ObjectId]} (${m.roles[Many[String]].mkString(",")})""").mkString(",")
      }
    }
    val fields = Seq[FldSpec](FldSpec("_id", primitiveFormatter), FldSpec("team_name", primitiveFormatter),
      FldSpec("group", primitiveFormatter), FldSpec("skill", csvFormatter), FldSpec("team_members", membersFormatter),
      FldSpec("organization_id", primitiveFormatter), FldSpec("project_id", primitiveFormatter))
    writer.println(fields.map(_.name).mkString("<tr><td>", "</td><td>", "</td></tr>"))
    val orgs: Seq[DynDoc] = BWMongoDB3.teams.find()
    for (org <- orgs) {
      val tds = fields.map(f => (f.name, f.asString(org.asDoc.getOrElse(f.name, null))))
      writer.println(tds.map(td => td._2).mkString("<tr><td>", "</td><td>", "</td></tr>"))
    }
  }

  def activitiesData(writer: PrintWriter): Unit = {
    val fields = Seq[FldSpec](FldSpec("_id", primitiveFormatter), FldSpec("name", primitiveFormatter),
      FldSpec("bpmn_id", primitiveFormatter), FldSpec("bpmn_name", primitiveFormatter),
      FldSpec("bpmn_name_full", primitiveFormatter), FldSpec("full_path_id", primitiveFormatter),
      FldSpec("full_path_name", primitiveFormatter), FldSpec("is_takt", booleanFormatter),
      FldSpec("offset", primitiveFormatter), FldSpec("takt_unit_no", primitiveFormatter))
    writer.println(fields.map(_.name).mkString("<tr><td>", "</td><td>", "</td></tr>"))
    val orgs: Seq[DynDoc] = BWMongoDB3.activities.find()
    for (org <- orgs) {
      val tds = fields.map(f => (f.name, f.asString(org.asDoc.getOrElse(f.name, null))))
      writer.println(tds.map(td => td._2).mkString("<tr><td>", "</td><td>", "</td></tr>"))
    }
  }

  private def reportData(response: HttpServletResponse): Unit = {
    response.setContentType("text/html")
    val writer = response.getWriter
    writer.println("<html><body>")
    writer.println("<h2>Organizations</h2>")
    writer.println("""<table id="organizations" border="1">""")
    orgData(writer)
    writer.println("</table>")
    writer.println("<h2>Persons</h2>")
    writer.println("""<table id="persons" border="1">""")
    personData(writer)
    writer.println("</table>")
    writer.println("<h2>Teams</h2>")
    writer.println("""<table id="teams" border="1">""")
    teamData(writer)
    writer.println("</table>")
    writer.println("<h2>Activities</h2>")
    writer.println("""<table id="activities" border="1">""")
    activitiesData(writer)
    writer.println("</table>")
    writer.println("</body></html>")
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameterMap = getParameterMap(request)
    val key = parameterMap("key")

    reportData(response)
    BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
  }

}