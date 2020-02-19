package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.jelly.ActivityHandlerEnd
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class TaskStatusReport extends HttpServlet with HttpUtils with DateTimeUtils {

  private def handleNewStatus(status: String, user: DynDoc, activityOid: ObjectId, comments: String,
        optPercentComplete: Option[String], endDates: Seq[(String, Long)]): Unit = {

    if (endDates.nonEmpty) {
      BWMongoDB3.activities.updateOne(Map("_id" -> activityOid), Map($set -> endDates.toMap))
    }

    def superUsersAssignment(assignment: DynDoc, user: DynDoc): Boolean = {
      val parentProcess = ActivityApi.parentProcess(assignment.activity_id[ObjectId])
      ProcessApi.canManage(user._id[ObjectId], parentProcess)
    }

    val allAssignments = ActivityApi.teamAssignment.list(activityOid)
    val usersAssignments = allAssignments.
        filter(assignment => assignment.status[String].matches("started|active") &&
            (assignment.person_id[ObjectId] == user._id[ObjectId] || superUsersAssignment(assignment, user)))
    val approvals = Seq(RoleListSecondary.preApproval, RoleListSecondary.postApproval)
    val (preApprovals, postApprovals) = allAssignments.filter(a => approvals.contains(a.role[String])).
        partition(_.role[String] == RoleListSecondary.preApproval)
    val secondaryRoles = RoleListSecondary.secondaryRoles

    if (status.matches("(?i)Pre-Approval-OK")) {
      usersAssignments.find(a => a.role[String] == RoleListSecondary.preApproval) match {
        case Some(assignment) =>
          ActivityApi.teamAssignment.assignmentEnd(assignment._id[ObjectId])
          assignment.status = "ended"
        case None =>
          throw new IllegalArgumentException(s"Unable to find matching assignment")
      }
      val pendingPreApprovals = preApprovals.filter(_.status[String] != "ended")
      if (pendingPreApprovals.isEmpty) {
        allAssignments.find(a => !secondaryRoles.contains(a.role[String])) match {
          case Some(primaryAssignment) =>
            ActivityApi.teamAssignment.assignmentStart(primaryAssignment._id[ObjectId])
          case None =>
            throw new IllegalArgumentException("Unable to find primary-role assignment")
        }
      }
    } else if (status.matches("(?i)Complete")) {
      usersAssignments.find(a => !secondaryRoles.contains(a.role[String])) match {
        case Some(primaryAssignment) =>
          ActivityApi.teamAssignment.assignmentEnd(primaryAssignment._id[ObjectId])
        case None =>
          throw new IllegalArgumentException(s"Unable to find matching assignment")
      }
      if (postApprovals.nonEmpty) {
        for (assignment <- postApprovals) {
          ActivityApi.teamAssignment.assignmentStart(assignment._id[ObjectId])
        }
      } else {
        ActivityHandlerEnd.end(activityOid, signal=true)
      }
    } else if (status.matches("(?i)Post-Approval-OK")) {
      usersAssignments.find(a => a.role[String] == RoleListSecondary.postApproval) match {
        case Some(assignment) =>
          ActivityApi.teamAssignment.assignmentEnd(assignment._id[ObjectId])
          assignment.status = "ended"
        case None =>
          throw new IllegalArgumentException(s"Unable to find matching assignment")
      }
      val pendingPostApprovals = postApprovals.filter(_.status[String] != "ended")
      if (pendingPostApprovals.isEmpty) {
        ActivityHandlerEnd.end(activityOid, signal=true)
      }
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val user: DynDoc = getUser(request)
      val timeZone = user.tz[String]
      val activityOid = new ObjectId(parameters("activity_id"))
      val optPercentComplete = parameters.get("percent_complete")
      if (optPercentComplete.map(_.toFloat).exists(pc => pc < 0 || pc > 100))
        throw new IllegalArgumentException(s"Bad percent-complete: '$optPercentComplete'")
      val comments = parameters("comments")
      val status = parameters("status")
      val endDates: Seq[(String, Long)] = Seq("end_date_optimistic", "end_date_likely", "end_date_pessimistic").
          filter(parameters.contains).map(paramName => (paramName, milliseconds(parameters(paramName), Some(timeZone))))

      ActivityApi.addChangeLogEntry(activityOid, s"$status: $comments", Some(user._id[ObjectId]), optPercentComplete)
      handleNewStatus(status, user, activityOid, comments, optPercentComplete, endDates)

      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}