package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class DashboardEntries extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      val entries: Seq[DynDoc] = DashboardEntries.dashboardEntries(freshUserRecord)

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

object DashboardEntries extends DateTimeUtils {

  private def compareDashboardEntries(a: DynDoc, b: DynDoc): Boolean = {
    val levels = Map("urgent" -> 3, "important" -> 2, "normal" -> 1)
    val (lvla, lvlb) = (levels(a.status[String]), levels(b.status[String]))
    val (dda, ddb) = (a.due_date[String], b.due_date[String])
    (lvla > lvlb) || (lvla == lvlb) && (dda < ddb)
  }

  def dashboardEntries(user: DynDoc): Seq[DynDoc] = {
    val timeZone = user.tz[String]
    val userOid = user._id[ObjectId]

    val projects: Seq[DynDoc] = ProjectApi.projectsByUser(userOid)
    val projectPhasePairs: Seq[(DynDoc, DynDoc)] = projects.
      flatMap(project => ProjectApi.phasesByUser(userOid, project).map(phase => (project, phase)))

    val dashboardEntries: Seq[DynDoc] = projectPhasePairs.map(pair => {
      val (projectName, projectOid) = (pair._1.name[String], pair._1._id[ObjectId])
      val (phaseName, phaseOid) = (pair._2.name[String], pair._2._id[ObjectId])
      val params: String = Seq(
        s"project_id=${pair._1._id[ObjectId]}",
        s"project_name=${pair._1.name[String]}",
        s"phase_id=${pair._2._id[ObjectId]}",
        s"phase_name=${pair._2.name[String]}"
      ).mkString("?", "&", "")
      val phaseDisplayStatus = PhaseApi.displayStatus(pair._2)
      val statusTime = pair._2.timestamps[Document].values.asScala.map(_.asInstanceOf[Long]).max
      val statusDate = dateTimeString(statusTime, Some(timeZone))
      val tasksOverdue = PhaseApi.allProcesses(phaseOid).filter(ProcessApi.isActive).
        flatMap(proc => ProcessApi.allActivities(Right(proc))).count(proc => ActivityApi.isDelayed(proc))
      Map(
        "project_name" -> projectName,
        "project_id" -> projectOid.toString,
        "phase_name" -> phaseName,
        "phase_id" -> phaseOid.toString,
        "tasks_overdue" -> Map("value" -> f"$tasksOverdue%03d", "url" -> ("/task-list" + params + "&type=Overdue")),
        "rfis_open" -> Map("value" -> "000", "url" -> ("/rfis" + params + "&type=Open")),
        "issues_open" -> Map("value" -> "000", "url" -> ("/etc" + params + "&type=Open")),
        "submittals_pending" -> Map("value" -> "000", "url" -> ("/etc" + params + "&type=Pending")),
        "submittals_unapproved" -> Map("value" -> "000", "url" -> ("/etc" + params + "&type=Unapproved")),
        "new_docs" -> Map("value" -> "000", "url" -> ("/documents" + params + "&type=New")),
        "material_issues" -> Map("value" -> "000", "url" -> ("/etc" + params + "&type=Issues")),
        "equipment_issues" -> Map("value" -> "000", "url" -> ("/etc" + params + "&type=Issues")),
        "invoices_payable" -> Map("value" -> "000", "url" -> ("/etc" + params + "&type=Payable")),
        "budget" -> Map("value" -> "000", "url" -> ("/etc" + params)),
        "expenses_so_far" -> Map("value" -> "000", "url" -> ("/etc" + params + "&type=So-Far")),
        "excess_expenses_so_far" -> Map("value" -> "000", "url" -> ("/etc" + params + "&type=Excess-so-Far")),

        "status_date" -> statusDate, "status" -> phaseDisplayStatus, "due_date" -> "0000-00-00",
        "display_status" -> phaseDisplayStatus
      )
    })

    dashboardEntries//.sortWith(compareDashboardEntries)
  }

}