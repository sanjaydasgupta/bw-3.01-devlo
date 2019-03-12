package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class TaskStatusReport extends HttpServlet with HttpUtils with DateTimeUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val user: DynDoc = getUser(request)
      val activityOid = new ObjectId(parameters("activity_id"))
      val percentComplete = parameters("percent_complete")
      if (percentComplete.toInt < 0 || percentComplete.toInt > 100)
        throw new IllegalArgumentException(s"Bad percent-complete: '$percentComplete'")
      val comments = parameters("comments")
      val status = parameters("status")
      if (!status.matches("(?i)complete|accepted|rejected|in-progress"))
        throw new IllegalArgumentException(s"Bad status: '$status'")

      ActivityApi.addChangeLogEntry(activityOid, comments, Some(user._id[ObjectId]), Some(percentComplete))
      if (status.matches("(?i)complete")) {
        //
      } else if (status.matches("(?i)accepted")) {
        //
      } else if (status.matches("(?i)rejected")) {
        //
      }

//      val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
//          Map("$set" -> Map()))
//      if (updateResult.getMatchedCount == 0)
//        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
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