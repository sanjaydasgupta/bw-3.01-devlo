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
import scala.annotation.tailrec

class BIDataConnector extends HttpServlet with RestUtils {

  private def phoneFormatter(rec: Any): String = {
    rec.asInstanceOf[Many[Document]].filter(_.phone[String].nonEmpty).
      map(em => s"${em.phone[String]} (${em.`type`[String]})").mkString(", ")
  }

  private def emailFormatter(rec: Any): String = {
    rec.asInstanceOf[Many[Document]].filter(_.email[String].nonEmpty).
      map(em => s"${em.email[String]} (${em.`type`[String]})").mkString(", ")
  }

  private def booleanFormatter(field: Any): String = {
    if (field == null) "false" else field.toString
  }

  private def primitiveFormatter(field: Any): String = {
    if (field == null) "" else field.toString
  }

  private def csvFormatter(field: Any): String = {
    if (field == null) "" else field.asInstanceOf[Many[_]].mkString(", ")
  }

  @tailrec
  private def fieldValue(record: DynDoc, fieldPath: String): Any = {
    fieldPath.split("[.]").toSeq match {
      case fieldName +: Nil =>
        if (record != null && record.asDoc != null) {
          record.getOrElse(fieldName, null)
        } else {
          null
        }
      case prefix +: subPath =>
        if (record != null && record.asDoc != null) {
          fieldValue(record.getOrElse[Document](prefix, null), subPath.mkString("."))
        } else {
          null
        }
    }
  }

  case class FldSpec(name: String, asString: Any => String)

  private def projectsData(writer: PrintWriter, json: Boolean): Unit = {
    val projects: Seq[DynDoc] = BWMongoDB3.projects.find()
    if (json) {
      val jsons = projects.map(_.asDoc.toJson).mkString("[", ", ", "], ")
      writer.print("\"projects\": " + jsons)
    } else {
      val fields = Seq[FldSpec](FldSpec("_id", primitiveFormatter), FldSpec("name", primitiveFormatter),
        FldSpec("phase_ids", csvFormatter))
      writer.println("<h2>Projects</h2>")
      writer.println("""<table id="projects" border="1" types="s,s,csv">""")
      writer.println(fields.map(_.name).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      for (project <- projects) {
        val tds = fields.map(f => (f.name, f.asString(fieldValue(project, f.name))))
        writer.println(tds.map(td => td._2).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      }
      writer.println("</table>")
    }
  }

  private def phasesData(writer: PrintWriter, json: Boolean): Unit = {
    val phases: Seq[DynDoc] = BWMongoDB3.phases.find()
    if (json) {
      val jsons = phases.map(_.asDoc.toJson).mkString("[", ", ", "], ")
      writer.print("\"phases\": " + jsons)
    } else {
      def teamAssignmentsFormatter(teamAssignments: Any): String = {
        if (teamAssignments == null) {
          ""
        } else {
          teamAssignments.asInstanceOf[Many[Document]].map(m => m.team_id[ObjectId]).mkString(", ")
        }
      }
      val fields = Seq[FldSpec](FldSpec("_id", primitiveFormatter), FldSpec("name", primitiveFormatter),
        FldSpec("process_ids", csvFormatter), FldSpec("team_assignments", teamAssignmentsFormatter))
      writer.println("<h2>Phases</h2>")
      writer.println("""<table id="phases" border="1" types="s,s,csv,csv">""")
      writer.println(fields.map(_.name).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      for (phase <- phases) {
        val tds = fields.map(f => (f.name, f.asString(fieldValue(phase, f.name))))
        writer.println(tds.map(td => td._2).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      }
      writer.println("</table>")
    }
  }

  private def processesData(writer: PrintWriter, json: Boolean): Unit = {
    val processes: Seq[DynDoc] = BWMongoDB3.processes.find()
    if (json) {
      val jsons = processes.map(_.asDoc.toJson).mkString("[", ", ", "], ")
      writer.print("\"processes\": " + jsons)
    } else {
      val fields = Seq[FldSpec](FldSpec("_id", primitiveFormatter), FldSpec("name", primitiveFormatter),
        FldSpec("activity_ids", csvFormatter))
      writer.println("<h2>Processes</h2>")
      writer.println("""<table id="processes" border="1" types="s,s,csv">""")
      writer.println(fields.map(_.name).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      for (process <- processes) {
        val tds = fields.map(f => (f.name, f.asString(fieldValue(process, f.name))))
        writer.println(tds.map(td => td._2).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      }
      writer.println("</table>")
    }
  }

  private def personData(writer: PrintWriter, json: Boolean): Unit = {
    val persons: Seq[DynDoc] = BWMongoDB3.persons.find()
    if (json) {
      val jsons = persons.map(_.asDoc.toJson).mkString("[", ", ", "], ")
      writer.print("\"persons\": " + jsons)
    } else {
      val fields = Seq[FldSpec](FldSpec("_id", primitiveFormatter), FldSpec("first_name", primitiveFormatter),
        FldSpec("last_name", primitiveFormatter), FldSpec("organization_id", primitiveFormatter),
        FldSpec("enabled", booleanFormatter), FldSpec("emails", emailFormatter), FldSpec("phones", phoneFormatter),
        FldSpec("rating", primitiveFormatter), FldSpec("skills", csvFormatter),
        FldSpec("years_experience", primitiveFormatter), FldSpec("slack_id", primitiveFormatter))
      writer.println("<h2>Persons</h2>")
      writer.println("""<table id="persons" border="1" types="s,s,s,s,b,csv,csv,i,csv,f,s">""")
      writer.println(fields.map(_.name).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      for (person <- persons) {
        val tds = fields.map(f => (f.name, f.asString(fieldValue(person, f.name))))
        writer.println(tds.map(td => td._2).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      }
      writer.println("</table>")
    }
  }

  private def organizationsData(writer: PrintWriter, json: Boolean): Unit = {
    val organizations: Seq[DynDoc] = BWMongoDB3.organizations.find()
    if (json) {
      val jsons = organizations.map(_.asDoc.toJson).mkString("[", ", ", "], ")
      writer.print("\"organizations\": " + jsons)
    } else {
      val fields = Seq[FldSpec](FldSpec("_id", primitiveFormatter), FldSpec("name", primitiveFormatter),
        FldSpec("active", booleanFormatter), FldSpec("areas_of_operation", csvFormatter),
        FldSpec("rating", primitiveFormatter), FldSpec("skills", csvFormatter),
        FldSpec("years_experience", primitiveFormatter), FldSpec("design_partner", booleanFormatter),
        FldSpec("project_sponsor", booleanFormatter), FldSpec("trade_partner", booleanFormatter))
      writer.println("<h2>Organizations</h2>")
      writer.println("""<table id="organizations" border="1" types="s,s,b,csv,i,csv,f,b,b,b">""")
      writer.println(fields.map(_.name).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      for (organization <- organizations) {
        val tds = fields.map(f => (f.name, f.asString(fieldValue(organization, f.name))))
        writer.println(tds.map(td => td._2).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      }
      writer.println("</table>")
    }
  }

  private def teamData(writer: PrintWriter, json: Boolean): Unit = {
    val teams: Seq[DynDoc] = BWMongoDB3.teams.find()
    if (json) {
      val jsons = teams.map(_.asDoc.toJson).mkString("[", ", ", "], ")
      writer.print("\"teams\": " + jsons)
    } else {
      def membersFormatter(members: Any): String = {
        if (members == null) {
          ""
        } else {
          members.asInstanceOf[Many[Document]].map(m =>
            s"""${m.person_id[ObjectId]} (${m.roles[Many[String]].mkString(", ")})""").mkString(", ")
        }
      }
      val fields = Seq[FldSpec](FldSpec("_id", primitiveFormatter), FldSpec("team_name", primitiveFormatter),
        FldSpec("group", primitiveFormatter), FldSpec("skill", csvFormatter), FldSpec("team_members", membersFormatter),
        FldSpec("organization_id", primitiveFormatter), FldSpec("project_id", primitiveFormatter),
        FldSpec("color", primitiveFormatter))
      writer.println("<h2>Teams</h2>")
      writer.println("""<table id="teams" border="1" types="s,s,s,csv,csv,s,s,s">""")
      writer.println(fields.map(_.name).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      for (team <- teams) {
        val tds = fields.map(f => (f.name, f.asString(fieldValue(team, f.name))))
        writer.println(tds.map(td => td._2).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      }
      writer.println("</table>")
    }
  }

  private def tasksData(writer: PrintWriter, json: Boolean): Unit = {
    val activities: Seq[DynDoc] = BWMongoDB3.tasks.find()
    if (json) {
      val jsons = activities.map(_.asDoc.toJson).mkString("[", ", ", "], ")
      writer.print("\"tasks\": " + jsons)
    } else {
      val fields = Seq[FldSpec](FldSpec("_id", primitiveFormatter), FldSpec("name", primitiveFormatter),
        FldSpec("bpmn_id", primitiveFormatter), FldSpec("bpmn_name", primitiveFormatter),
        FldSpec("bpmn_name_full", primitiveFormatter), FldSpec("full_path_id", primitiveFormatter),
        FldSpec("full_path_name", primitiveFormatter), FldSpec("is_takt", booleanFormatter),
        FldSpec("offset", primitiveFormatter), FldSpec("takt_unit_no", primitiveFormatter),
        FldSpec("durations.likely", primitiveFormatter))
      writer.println("<h2>Tasks</h2>")
      writer.println("""<table id="tasks" border="1" types="s,s,s,s,s,s,s,b,i,i,i">""")
      writer.println(fields.map(_.name).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      for (activity <- activities) {
        val tds = fields.map(f => (f.name, f.asString(fieldValue(activity, f.name))))
        writer.println(tds.map(td => td._2).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      }
      writer.println("</table>")
    }
  }

  private def deliverablesData(writer: PrintWriter, json: Boolean): Unit = {
    val deliverables: Seq[DynDoc] = BWMongoDB3.deliverables.find()
    if (json) {
      val jsons = deliverables.map(_.asDoc.toJson).mkString("[", ", ", "], ")
      writer.print("\"deliverables\": " + jsons)
    } else {
      def teamAssignmentsFormatter(assignments: Any): String = {
        if (assignments == null) {
          ""
        } else {
          assignments.asInstanceOf[Many[Document]].
            map(ta => s"${ta.team_id[ObjectId]} (${ta.role[String]})").mkString(", ")
        }
      }
      def checkListFormatter(checkList: Any): String = {
        if (checkList == null) {
          ""
        } else {
          checkList.asInstanceOf[Many[Document]].map(_.text[String]).mkString(", ")
        }
      }
      val fields = Seq[FldSpec](FldSpec("_id", primitiveFormatter), FldSpec("name", primitiveFormatter),
        FldSpec("activity_id", primitiveFormatter), FldSpec("deliverable_type", primitiveFormatter),
        FldSpec("is_takt", booleanFormatter), FldSpec("status", primitiveFormatter),
        FldSpec("common_instance_no", primitiveFormatter), FldSpec("takt_unit_no", primitiveFormatter),
        FldSpec("duration", primitiveFormatter), FldSpec("date_end_estimated", primitiveFormatter),
        FldSpec("team_assignments", teamAssignmentsFormatter), FldSpec("description", primitiveFormatter),
        FldSpec("wbs_code", primitiveFormatter), FldSpec("crew_size", primitiveFormatter),
        FldSpec("total_quantity", primitiveFormatter), FldSpec("unit", primitiveFormatter),
        FldSpec("check_list", checkListFormatter), FldSpec("contact_person_id", primitiveFormatter))
      writer.println("<h2>Deliverables</h2>")
      writer.println("""<table id="deliverables" border="1" types="s,s,s,s,b,s,i,i,i,l,csv,s,s,i,f,s,csv,s">""")
      writer.println(fields.map(_.name).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      for (deliverable <- deliverables) {
        val tds = fields.map(f => (f.name, f.asString(fieldValue(deliverable, f.name))))
        writer.println(tds.map(td => td._2).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      }
      writer.println("</table>")
    }
  }

  private def constraintsData(writer: PrintWriter, json: Boolean): Unit = {
    val constraints: Seq[DynDoc] = BWMongoDB3.constraints.find()
    if (json) {
      val jsons = constraints.map(_.asDoc.toJson).mkString("[", ", ", "], ")
      writer.print("\"constraints\": " + jsons)
    } else {
      val fields = Seq[FldSpec](FldSpec("_id", primitiveFormatter), FldSpec("type", primitiveFormatter),
        FldSpec("owner_deliverable_id", primitiveFormatter), FldSpec("constraint_id", primitiveFormatter),
        FldSpec("delay", primitiveFormatter), FldSpec("is_replicable", booleanFormatter),
        FldSpec("common_set_no", primitiveFormatter), FldSpec("direction", primitiveFormatter))
      writer.println("<h2>Constraints</h2>")
      writer.println("""<table id="constraints" border="1" types="s,s,s,s,i,b,i,s">""")
      writer.println(fields.map(_.name).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      for (constraint <- constraints) {
        val tds = fields.map(f => (f.name, f.asString(fieldValue(constraint, f.name))))
        writer.println(tds.map(td => td._2).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      }
      writer.println("</table>")
    }
  }

  private def reportData(response: HttpServletResponse, json: Boolean): Unit = {
    response.setContentType(if (json) "application/json" else "text/html")
    val writer = response.getWriter
    writer.print(if (json) "{" else "<html><body>")
    projectsData(writer, json)
    phasesData(writer, json)
    processesData(writer, json)
    organizationsData(writer, json)
    personData(writer, json)
    teamData(writer, json)
    tasksData(writer, json)
    deliverablesData(writer, json)
    constraintsData(writer, json)
    writer.print(if (json) "\"status\": 1}" else "</body></html>")
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameterMap = getParameterMap(request)
    val key = parameterMap("key")

    reportData(response, key == "json")
    BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
  }

}