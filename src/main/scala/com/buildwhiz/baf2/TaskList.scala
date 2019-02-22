package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class TaskList extends HttpServlet with HttpUtils with DateTimeUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      val freshUserRecord = PersonApi.personById(userOid)
      val tasks: Seq[DynDoc] = ActivityApi.actionsByUser(userOid)

      response.getWriter.print(tasks.map(task => task2json(task, freshUserRecord)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${tasks.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  private def task2json(action: DynDoc, user: DynDoc): String = {
    val activityOid = action.activity_id[ObjectId]

    val viewAction: DynDoc = new Document()

    viewAction.name = action.name[String]
    viewAction.status = action.status[String]
    viewAction.`type` = action.`type`[String]

    val (startDateTime, endDateTime) = if (action.has("timestamps")) {
      val timezone = user.tz[String]
      val timestamps: DynDoc = action.timestamps[Document]
      if (timestamps.has("end"))
        (dateTimeString(timestamps.start[Long], Some(timezone)), dateTimeString(timestamps.end[Long], Some(timezone)))
      else if (timestamps.has("start"))
        (dateTimeString(timestamps.start[Long], Some(timezone)), "NA")
      else
        ("NA", "NA")
    } else {
      ("NA", "NA")
    }
    viewAction.start_datetime = startDateTime
    viewAction.end_datetime = endDateTime

    val theProcess = ActivityApi.parentProcess(activityOid)
    viewAction.process_name = theProcess.name[String]
    viewAction.process_id = theProcess._id[ObjectId]

    val thePhase = ProcessApi.parentPhase(theProcess._id[ObjectId])
    viewAction.phase_name = thePhase.name[String]
    viewAction.phase_id = thePhase._id[ObjectId]

    val theProject = PhaseApi.parentProject(thePhase._id[ObjectId])
    viewAction.project_name = theProject.name[String]
    viewAction.project_id = theProject._id[ObjectId]

    val theActivity = ActivityApi.activityById(activityOid)
    viewAction.activity_name = theActivity.name[String]
    viewAction.activity_id = theActivity._id[ObjectId]
    viewAction.activity_description = theActivity.description[String]

    bson2json(viewAction.asDoc)
  }

}