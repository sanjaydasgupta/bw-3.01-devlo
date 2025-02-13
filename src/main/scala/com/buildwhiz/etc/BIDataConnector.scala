package com.buildwhiz.etc

import com.buildwhiz.api.RestUtils
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import org.bson.Document
import org.bson.types.ObjectId

import java.io.PrintWriter
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

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

  private val oidRegex = """[{]"[$]oid": ("[0-9a-f]{24}")[}]"""
  case class FldSpec(name: String, asString: Any => String)

  private def projectsData(writer: PrintWriter, json: Boolean, query: Many[Document] = Seq.empty[Document]): Unit = {
    val projects: Seq[DynDoc] = BWMongoDB3.projects.aggregate(query)
    if (json) {
      val jsons = projects.map(_.asDoc.toJson.replaceAll(oidRegex, "$1")).mkString("\n")
      writer.print(jsons)
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

  private def phasesData(writer: PrintWriter, json: Boolean, query: Many[Document] = Seq.empty[Document]): Unit = {
    val phases: Seq[DynDoc] = BWMongoDB3.phases.aggregate(query)
    if (json) {
      val jsons = phases.map(_.asDoc.toJson.replaceAll(oidRegex, "$1")).mkString("\n")
      writer.print(jsons)
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

  private def processesData(writer: PrintWriter, json: Boolean, query: Many[Document] = Seq.empty[Document]): Unit = {
    val processes: Seq[DynDoc] = BWMongoDB3.processes.aggregate(query)
    if (json) {
      val jsons = processes.map(_.asDoc.toJson.replaceAll(oidRegex, "$1")).mkString("\n")
      writer.print(jsons)
    } else {
      val fields = Seq[FldSpec](FldSpec("_id", primitiveFormatter), FldSpec("name", primitiveFormatter),
        FldSpec("activity_ids", csvFormatter), FldSpec("type", primitiveFormatter),
        FldSpec("template_process_id", primitiveFormatter))
      writer.println("<h2>Processes</h2>")
      writer.println("""<table id="processes" border="1" types="s,s,csv,s,s">""")
      writer.println(fields.map(_.name).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      for (process <- processes) {
        val tds = fields.map(f => (f.name, f.asString(fieldValue(process, f.name))))
        writer.println(tds.map(td => td._2).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      }
      writer.println("</table>")
    }
  }

  private def processSchedulesData(writer: PrintWriter, json: Boolean, query: Many[Document] = Seq.empty[Document]): Unit = {
    val processSchedules: Seq[DynDoc] = BWMongoDB3.process_schedules.aggregate(query)
    if (json) {
      val jsons = processSchedules.map(_.asDoc.toJson.replaceAll(oidRegex, "$1")).mkString("\n")
      writer.print(jsons)
    } else {
      val fields = Seq[FldSpec](FldSpec("_id", primitiveFormatter), FldSpec("title", primitiveFormatter),
        FldSpec("description", primitiveFormatter), FldSpec("frequency", primitiveFormatter),
        FldSpec("status", primitiveFormatter))
      writer.println("<h2>Processes-Schedules</h2>")
      writer.println("""<table id="processSchedules" border="1" types="s,s,s,s,s">""")
      writer.println(fields.map(_.name).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      for (process <- processSchedules) {
        val tds = fields.map(f => (f.name, f.asString(fieldValue(process, f.name))))
        writer.println(tds.map(td => td._2).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      }
      writer.println("</table>")
    }
  }

  private def personsData(writer: PrintWriter, json: Boolean, query: Many[Document] = Seq.empty[Document]): Unit = {
    val persons: Seq[DynDoc] = BWMongoDB3.persons.aggregate(query)
    if (json) {
      val jsons = persons.map(_.asDoc.toJson.replaceAll(oidRegex, "$1")).mkString("\n")
      writer.print(jsons)
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

  private def organizationsData(writer: PrintWriter, json: Boolean, query: Many[Document] = Seq.empty[Document]): Unit = {
    val organizations: Seq[DynDoc] = BWMongoDB3.organizations.aggregate(query)
    if (json) {
      val jsons = organizations.map(_.asDoc.toJson.replaceAll(oidRegex, "$1")).mkString("\n")
      writer.print(jsons)
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

  private def teamsData(writer: PrintWriter, json: Boolean, query: Many[Document] = Seq.empty[Document]): Unit = {
    val teams: Seq[DynDoc] = BWMongoDB3.teams.aggregate(query)
    if (json) {
      val jsons = teams.map(_.asDoc.toJson.replaceAll(oidRegex, "$1")).mkString("\n")
      writer.print(jsons)
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

  private def tasksData(writer: PrintWriter, json: Boolean, query: Many[Document] = Seq.empty[Document]): Unit = {
    val tasks: Seq[DynDoc] = BWMongoDB3.tasks.aggregate(query)
    for (aTask <- tasks) {
      for (entry <- aTask.asDoc.entrySet().asScala) {
        entry.getValue match {
          case value: String =>
            aTask.asDoc.put(entry.getKey, value.replaceAll("\u00a0", " "))
          case _ =>
        }
      }
    }
    if (json) {
      val jsons = tasks.map(_.asDoc.toJson.replaceAll(oidRegex, "$1")).mkString("\n")
      writer.print(jsons)
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
      for (activity <- tasks) {
        val tds = fields.map(f => (f.name, f.asString(fieldValue(activity, f.name))))
        writer.println(tds.map(td => td._2).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      }
      writer.println("</table>")
    }
  }

  private def deliverablesData(writer: PrintWriter, json: Boolean, query: Many[Document] = Seq.empty[Document]): Unit = {
    val deliverables: Seq[DynDoc] = BWMongoDB3.deliverables.aggregate(query)
    if (json) {
      val jsons = deliverables.map(_.asDoc.toJson.replaceAll(oidRegex, "$1")).mkString("\n")
      writer.print(jsons)
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

  private def constraintsData(writer: PrintWriter, json: Boolean, query: Many[Document] = Seq.empty[Document]): Unit = {
    val constraints: Seq[DynDoc] = BWMongoDB3.constraints.aggregate(query)
    if (json) {
      val jsons = constraints.map(_.asDoc.toJson.replaceAll(oidRegex, "$1")).mkString("\n")
      writer.print(jsons)
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

  private def progressInfosData(writer: PrintWriter, json: Boolean, query: Many[Document] = Seq.empty[Document]): Unit = {
    val progressInfos: Seq[DynDoc] = BWMongoDB3.deliverables_progress_infos.aggregate(query)
    if (json) {
      val jsons = progressInfos.map(_.asDoc.toJson.replaceAll(oidRegex, "$1")).mkString("\n")
      writer.print(jsons)
    } else {
      val fields = Seq[FldSpec](FldSpec("_id", primitiveFormatter), FldSpec("type", primitiveFormatter),
        FldSpec("deliverable_id", primitiveFormatter), FldSpec("activity_id", primitiveFormatter),
        FldSpec("process_id", primitiveFormatter), FldSpec("phase_id", primitiveFormatter),
        FldSpec("team_id", primitiveFormatter), FldSpec("project_id", primitiveFormatter),
        FldSpec("timestamp", primitiveFormatter), FldSpec("event_type", primitiveFormatter),
        FldSpec("comment", primitiveFormatter), FldSpec("system_comment", primitiveFormatter),
        FldSpec("total_quantity2", primitiveFormatter), FldSpec("completed_quantity2", primitiveFormatter),
        FldSpec("percent_complete2", primitiveFormatter), FldSpec("event_type", primitiveFormatter))
      writer.println("<h2>ProgressInfosData</h2>")
      writer.println("""<table id="progressinfos" border="1" types="i,s,i,i,i,i,i,i,l,s,s,s,f,f,f,s">""")
      writer.println(fields.map(_.name).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      for (progressInfo <- progressInfos) {
        val tds = fields.map(f => (f.name, f.asString(fieldValue(progressInfo, f.name))))
        writer.println(tds.map(td => td._2).mkString("<tr><td>", "</td><td>", "</td></tr>"))
      }
      writer.println("</table>")
    }
  }

  private def traceLogData(writer: PrintWriter, query: Many[Document] = Seq.empty[Document]): Unit = {
    val constraints: Seq[DynDoc] = BWMongoDB3.trace_log.aggregate(query)
    val jsons = constraints.map(_.asDoc.toJson.replaceAll(oidRegex, "$1")).mkString("\n")
    writer.print(jsons)
  }

  private def fixDoc(aDoc: Document): Unit = {
    for (entry <- aDoc.asDoc.entrySet().asScala) {
      entry.getValue match {
        case value: String =>
          aDoc.asDoc.put(entry.getKey, value.replaceAll("\u00d7", "x"))
        case subDoc: Document => fixDoc(subDoc)
        case subDynDoc: DynDoc => fixDoc(subDynDoc.asDoc)
        case list: Many[_] => list.zipWithIndex.foreach {
          case (value: String, index: Int) => list.asInstanceOf[Many[Any]].set(index, value.replaceAll("\u00d7", "x"))
          case (subDoc: Document, _) => fixDoc(subDoc)
          case (subDynDoc: DynDoc, _) => fixDoc(subDynDoc.asDoc)
          case _ =>
        }
        case _ =>
      }
    }
  }

  private def documentMasterData(writer: PrintWriter, query: Many[Document] = Seq.empty[Document]): Unit = {
    val docs: Seq[DynDoc] = BWMongoDB3.document_master.aggregate(query)
    for (aDoc <- docs) {
      fixDoc(aDoc.asDoc)
    }
    val jsons = docs.map(_.asDoc.toJson.replaceAll(oidRegex, "$1")).mkString("\n")
    writer.print(jsons)
  }

  private def anyData(collectionName: String, writer: PrintWriter, query: Many[Document] = Seq.empty[Document]): Unit = {
    val records: Seq[DynDoc] = BWMongoDB3(collectionName).aggregate(query)
    val jsons = records.map(_.asDoc.toJson.replaceAll(oidRegex, "$1")).mkString("\n")
    writer.print(jsons)
  }

  private def reportData(response: HttpServletResponse): Unit = {
    response.setContentType("text/html")
    val writer = response.getWriter
    writer.print("<html><body>")
    projectsData(writer, json = false)
    phasesData(writer, json = false)
    processesData(writer, json = false)
    processSchedulesData(writer, json = false)
    organizationsData(writer, json = false)
    personsData(writer, json = false)
    teamsData(writer, json = false)
    tasksData(writer, json = false)
    deliverablesData(writer, json = false)
    constraintsData(writer, json = false)
    progressInfosData(writer, json = false)
    writer.print("</body></html>")
  }

  private def aggregationSpecification(key: String): (String, Many[Document]) = {
    val aggSpecDoc = Document.parse(key)
    val entries = aggSpecDoc.entrySet().asScala
    if (entries.size == 1) {
      val collectionName = entries.head.getKey
      val aggregation = entries.head.getValue.asInstanceOf[Many[Document]]
      (collectionName, aggregation)
    } else {
      throw new IllegalArgumentException(s"Bad key (need '{coll-name: agg-pipeline}'): '$key'")
    }
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameterMap = getParameterMap(request)
    val writer = response.getWriter
    response.setContentType("text/plain")
    parameterMap.get("key") match {
      case None =>
        reportData(response)
      case Some(key) =>
        val (collectionName, aggregationPipeline) = aggregationSpecification(key)
        collectionName match {
          case "projects" => projectsData(writer, json = true, aggregationPipeline)
          case "organizations" => organizationsData(writer, json = true, aggregationPipeline)
          case "phases" => phasesData(writer, json = true, aggregationPipeline)
          case "processes" => processesData(writer, json = true, aggregationPipeline)
          case "process_schedules" => processSchedulesData(writer, json = true, aggregationPipeline)
          case "persons" => personsData(writer, json = true, aggregationPipeline)
          case "teams" => teamsData(writer, json = true, aggregationPipeline)
          case "tasks" => tasksData(writer, json = true, aggregationPipeline)
          case "deliverables" => deliverablesData(writer, json = true, aggregationPipeline)
          case "constraints" => constraintsData(writer, json = true, aggregationPipeline)
          case "trace_log" => traceLogData(writer, aggregationPipeline)
          case "document_master" => documentMasterData(writer, aggregationPipeline)
          case _ => anyData(collectionName, writer, aggregationPipeline)
        }
    }
    writer.flush()
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val writer = response.getWriter
    response.setContentType("text/plain")
    val postData = getStreamData(request)
    val (collectionName, aggregationPipeline) = aggregationSpecification(postData)
    collectionName match {
      case "projects" => projectsData(writer, json = true, aggregationPipeline)
      case "organizations" => organizationsData(writer, json = true, aggregationPipeline)
      case "phases" => phasesData(writer, json = true, aggregationPipeline)
      case "processes" => processesData(writer, json = true, aggregationPipeline)
      case "process_schedules" => processSchedulesData(writer, json = true, aggregationPipeline)
      case "persons" => personsData(writer, json = true, aggregationPipeline)
      case "teams" => teamsData(writer, json = true, aggregationPipeline)
      case "tasks" => tasksData(writer, json = true, aggregationPipeline)
      case "deliverables" => deliverablesData(writer, json = true, aggregationPipeline)
      case "constraints" => constraintsData(writer, json = true, aggregationPipeline)
      case "deliverables_progress_infos" => progressInfosData(writer, json = true, aggregationPipeline)
      case "trace_log" => traceLogData(writer, aggregationPipeline)
      case "document_master" => documentMasterData(writer, aggregationPipeline)
      case _ => anyData(collectionName, writer, aggregationPipeline)
    }
    writer.flush()
  }

}