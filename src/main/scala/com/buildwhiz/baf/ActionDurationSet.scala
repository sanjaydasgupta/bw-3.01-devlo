package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.HttpUtils
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import com.buildwhiz.infra.BWMongoDB3._
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class ActionDurationSet extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    def formatDuration(d: String): String = {
      val parts = d.split(":").map(_.toInt) match {
        case arr3@Array(a, b, c) => arr3
        case arr2@Array(b, c) => 0 +: arr2
        case arr1@Array(c) => 0 +: 0 +: arr1
      }
      parts.map(p => f"$p%02d").mkString(":")
    }
    try {
      val duration = parameters("duration")
      if (!duration.matches("(?:(?:\\d{1,2}\\:)?\\d{1,2}\\:)?\\d{1,2}"))
        throw new IllegalArgumentException("Bad duration format")
      val activityOid = new ObjectId(parameters("activity_id"))
      val theActivity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).asScala.head
      val actionNames: Seq[String] = theActivity.actions[DocumentList].map(_.name[String])
      val actionName = parameters("action_name")
      val actionIdx = actionNames.indexOf(actionName)
      val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
        Map("$set" -> Map(s"actions.$actionIdx.duration" -> formatDuration(duration))))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doPost", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
