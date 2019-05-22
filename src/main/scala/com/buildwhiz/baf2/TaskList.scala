package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
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
      val assignments: Seq[DynDoc] = BWMongoDB3.activity_assignments.find(Map("person_id" -> userOid))

      response.getWriter.print(assignments.map(assignment => assignment2json(assignment, freshUserRecord)).
          mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${assignments.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  private def assignment2json(assignment: DynDoc, user: DynDoc): String = {
    val activityOid = assignment.activity_id[ObjectId]

    val viewAction: DynDoc = new Document()

    val (startDateTime, endDateTime) = if (assignment.has("timestamps")) {
      val timezone = user.tz[String]
      val timestamps: DynDoc = assignment.timestamps[Document]
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

    viewAction.name = theActivity.name[String]
    viewAction.status = theActivity.status[String]
    viewAction.`type` = assignment.role[String]

    bson2json(viewAction.asDoc)
  }

}