package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class TaskList extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    val queryType = parameters.getOrElse("type", "none")
    try {
      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      val freshUserRecord = PersonApi.personById(userOid)

      val assignments = TaskList.uniqueAssignments(freshUserRecord, queryType)

      response.getWriter.print(assignments.map(a => bson2json(a.asDoc)).mkString("[", ", ", "]"))
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

}

object TaskList extends DateTimeUtils {

  def uniqueAssignments(user: DynDoc, queryType: String="none"): Seq[DynDoc] = {
    val userOid = user._id[ObjectId]
    val assignments: Seq[DynDoc] = if (PersonApi.isBuildWhizAdmin(Right(user)))
      BWMongoDB3.activity_assignments.find()
    else
      BWMongoDB3.activity_assignments.find(Map("person_id" -> userOid))

    val goodAssignments = assignments.filter(a => {
      val activity = ActivityApi.activityById(a.activity_id[ObjectId])
      ActivityApi.parentProcess(activity._id[ObjectId])

      val status = activity.status[String]
      val queryTypeCondition = queryType match {
        case "Overdue" => status == "running" && ActivityApi.isDelayed(activity)
        case _ => true
      }
      queryTypeCondition
    })

    goodAssignments.groupBy(_.activity_id[ObjectId]).toSeq.map(t => {
      val seq = t._2
      val roles = seq.map(_.role[String])
      val assignment = seq.head
      assignment.role = roles.mkString(", ")

      val theProcess = ProcessApi.processById(assignment.process_id[ObjectId])
      assignment.process_name = theProcess.name[String]
      assignment.process_id = theProcess._id[ObjectId]

      val thePhase = PhaseApi.phaseById(assignment.phase_id[ObjectId])
      assignment.phase_name = thePhase.name[String]
      assignment.phase_id = thePhase._id[ObjectId]

      val theProject = ProjectApi.projectById(assignment.project_id[ObjectId])
      assignment.project_name = theProject.name[String]
      assignment.project_id = theProject._id[ObjectId]

      val theActivity = ActivityApi.activityById(assignment.activity_id[ObjectId])
      assignment.activity_name = theActivity.name[String]
      assignment.activity_id = theActivity._id[ObjectId]
      assignment.activity_description = theActivity.description[String]

      assignment.bpmn_name = theActivity.bpmn_name[String]
      assignment.name = theActivity.name[String]
      assignment.status = theActivity.status[String]
      assignment.`type` = assignment.role[String]

      val (startDateTime, endDateTime) = if (assignment.has("timestamps")) {
        val timezone = user.tz[String]
        val timestamps: DynDoc = assignment.timestamps[Document]
        (timestamps.has("start"), timestamps.has("end")) match {
          case (true, true) => (dateTimeString(timestamps.start[Long], Some(timezone)),
            dateTimeString(timestamps.end[Long], Some(timezone)))
          case (true, false) => (dateTimeString(timestamps.start[Long], Some(timezone)), "NA")
          case (false, true) => ("NA", dateTimeString(timestamps.end[Long], Some(timezone)))
          case (false, false) => ("NA", "NA")
        }
      } else {
        ("NA", "NA")
      }
      assignment.start_datetime = startDateTime
      assignment.end_datetime = endDateTime

      assignment
    })

  }

}