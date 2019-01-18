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

  def compareDashboardEntries(a: Document, b: Document): Boolean = {
    val levels = Map("urgent" -> 3, "important" -> 2, "normal" -> 1)
    val (lvla, lvlb) = (levels(a.y.status[String]), levels(b.y.status[String]))
    val (dda, ddb) = (a.y.due_date[String], b.y.due_date[String])
    (lvla > lvlb) || (lvla == lvlb) && (dda < ddb)
  }

  private def dashboardEntries(user: DynDoc): Seq[Document] = {
    val timeZone = user.tz[String]
    val projectOids: Seq[ObjectId] = user.project_ids[Many[ObjectId]]
    val projects: Seq[DynDoc] = BWMongoDB3.projects.find(Map("_id" -> Map("$in" -> projectOids)))

    val dashboardProjects: Seq[Document] = projects.
        filter(_.admin_person_id[ObjectId] == user._id[ObjectId]).map(project => {
      val projectName = project.name[String]
      val projectStatus = project.status[String] match {
        case "defined" | "idle" => "urgent"
        case "running" => "normal"
        case _ => "important"
      }
      val statusTime = project.timestamps[Document].values.asScala.map(_.asInstanceOf[Long]).max
      val statusDate = dateTimeString(statusTime, Some(timeZone))
      Map("url" -> "projects", "description" -> s"Project '$projectName' is ${project.status[String]}",
        "status_date" -> statusDate, "status" -> projectStatus, "due_date" -> "0000-00-00")
    })

    val allPhases: Seq[DynDoc] = projects.flatMap(project => {
      val phaseOids = project.process_ids[Many[ObjectId]]
      val phases: Seq[DynDoc] = BWMongoDB3.processes.find(Map("_id" -> Map("$in" -> phaseOids)))
      phases
    })

    val dashboardPhases: Seq[Document] = allPhases.
      filter(_.admin_person_id[ObjectId] == user._id[ObjectId]).map(phase => {
      val phaseName = phase.name[String]
      val phaseStatus = phase.status[String] match {
        case "defined" => "urgent"
        case "running" => "normal"
        case _ => "important"
      }
      val statusTime = phase.timestamps[Document].values.asScala.map(_.asInstanceOf[Long]).max
      val statusDate = dateTimeString(statusTime, Some(timeZone))
      Map("url" -> "phases", "description" -> s"Phase '$phaseName' is ${phase.status[String]}",
        "status_date" -> statusDate, "status" -> phaseStatus, "due_date" -> "0000-00-00")
    })

    val activityOids: Seq[ObjectId] = allPhases.flatMap(_.activity_ids[Many[ObjectId]])
    val allActivities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityOids)))
    val allActions: Seq[DynDoc] = allActivities.flatMap(_.actions[Many[Document]])

    val dashboardActions: Seq[Document] = allActions.
        filter(_.assignee_person_id[ObjectId] == user._id[ObjectId]).map(action => {
      val actionName = action.name[String]
      val actionStatus = action.status[String] match {
        case "waiting" => "urgent"
        case "ended" => "normal"
        case _ => "important"
      }
      val statusDate = if (action.has("timestamps")) {
        val statusTime = action.timestamps[Document].values.asScala.map(_.asInstanceOf[Long]).max
        dateTimeString(statusTime, Some(timeZone))
      } else {
        "0000-00-00"
      }
      Map("url" -> "tasks", "description" -> s"Task '$actionName' is ${action.status[String]}",
        "status_date" -> statusDate, "status" -> actionStatus, "due_date" -> "0000-00-00")
    })

    (dashboardProjects ++ dashboardPhases ++ dashboardActions).sortWith(compareDashboardEntries)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      val entries: Seq[Document] = dashboardEntries(freshUserRecord)

      response.getWriter.println(entries.map(_.toJson).mkString("[", ", ", "]"))
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