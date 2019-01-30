package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class DashboardEntries extends HttpServlet with HttpUtils with DateTimeUtils {

  def compareDashboardEntries(a: DynDoc, b: DynDoc): Boolean = {
    val levels = Map("urgent" -> 3, "important" -> 2, "normal" -> 1)
    val (lvla, lvlb) = (levels(a.status[String]), levels(b.status[String]))
    val (dda, ddb) = (a.due_date[String], b.due_date[String])
    (lvla > lvlb) || (lvla == lvlb) && (dda < ddb)
  }

  private def dashboardEntries(user: DynDoc): Seq[DynDoc] = {
    val timeZone = user.tz[String]
    val userOid = user._id[ObjectId]

    val projects: Seq[DynDoc] = ProjectApi.projectsByUser(userOid)
    val projectPhasePairs: Seq[(DynDoc, DynDoc)] = projects.
      flatMap(project => ProjectApi.phasesByUser(userOid, project).map(phase => (project, phase)))

    val dashboardEntries: Seq[DynDoc] = projectPhasePairs.map(ppp => {
      val projectName = ppp._1.name[String]
      val phaseName = ppp._2.name[String]
      val params = s"?project_id=${ppp._1._id[ObjectId]}&phase_id=${ppp._2._id[ObjectId]}"
      val phaseStatus = ppp._2.status[String] match {
        case "defined" => "urgent"
        case "running" => "normal"
        case _ => "important"
      }
      val statusTime = ppp._2.timestamps[Document].values.asScala.map(_.asInstanceOf[Long]).max
      val statusDate = dateTimeString(statusTime, Some(timeZone))
      Map(
        "project" -> Map("value" -> projectName, "url" -> s"/project?project_id=${ppp._1._id[ObjectId]}"),
        "phase" -> Map("value" -> phaseName, "url" -> s"/phase?phase_id=${ppp._2._id[ObjectId]}"),
        "tasks_overdue" -> Map("value" -> "000", "url" -> ("/tasks" + params)),
        "rfis_open" -> Map("value" -> "000", "url" -> ("/rfis" + params)),
        "issues_open" -> Map("value" -> "000", "url" -> "/etc"),
        "submittals_pending" -> Map("value" -> "000", "url" -> "/etc"),
        "submittals_unapproved" -> Map("value" -> "000", "url" -> "/etc"),
        "new_docs" -> Map("value" -> "000", "url" -> ("/documents" + params)),
        "material_issues" -> Map("value" -> "000", "url" -> "/etc"),
        "equipment_issues" -> Map("value" -> "000", "url" -> "/etc"),
        "invoices_payable" -> Map("value" -> "000", "url" -> "/etc"),
        "budget" -> Map("value" -> "000", "url" -> "/etc"),
        "expenses_so_far" -> Map("value" -> "000", "url" -> "/etc"),
        "excess_expenses_so_far" -> Map("value" -> "000", "url" -> "/etc"),

        "url" -> ("/phases" + params), "description" -> "???",
        "status_date" -> statusDate, "status" -> phaseStatus, "due_date" -> "0000-00-00"
      )
    })

    dashboardEntries.sortWith(compareDashboardEntries)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      val entries: Seq[DynDoc] = dashboardEntries(freshUserRecord)

      response.getWriter.println(entries.map(_.asDoc.toJson).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${entries.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}