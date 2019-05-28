package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class TaskStatusReport extends HttpServlet with HttpUtils with DateTimeUtils {

  private def handleNewStatus(status: String, user: DynDoc, activityOid: ObjectId, comments: String,
        optPercentComplete: Option[String]): Unit = {
    if (status.matches("(?i)Pre-Approval-OK")) {
      ActivityApi.teamAssignment.list(activityOid).
          find(a => a.role[String] == "Pre-Approval" && a.person_id[ObjectId] == user._id[ObjectId]) match {
        case Some(assignment) =>
          val updateResult = BWMongoDB3.activity_assignments.updateOne(Map("_id" -> assignment._id[ObjectId]),
            Map($set -> Map("status" -> "ended")))
          if (updateResult.getMatchedCount == 0)
            throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
        case None =>
          throw new IllegalArgumentException(s"Unable to find matching assignment")
      }
    } else if (status.matches("(?i)Complete")) {
      //
    } else if (status.matches("(?i)Post-Approval-OK")) {
      //
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val user: DynDoc = getUser(request)
      val activityOid = new ObjectId(parameters("activity_id"))
      val optPercentComplete = parameters.get("percent_complete")
      if (optPercentComplete.map(_.toFloat).exists(pc => pc < 0 || pc > 100))
        throw new IllegalArgumentException(s"Bad percent-complete: '$optPercentComplete'")
      val comments = parameters("comments")
      val status = parameters("status")

      ActivityApi.addChangeLogEntry(activityOid, s"$status: $comments", Some(user._id[ObjectId]), optPercentComplete)
      handleNewStatus(status, user, activityOid, comments, optPercentComplete)

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