package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class ActionDurationSet extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val actionName = parameters("action_name")
      val duration = parameters("duration")
      val description = parameters.get("description")
      val activityOid = new ObjectId(parameters("activity_id"))
      ActionDurationSet.set(request, activityOid, actionName, duration, description)
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object ActionDurationSet {

  private def formatDuration(d: String): String = {
    val parts = d.split(":").map(_.toInt) match {
      case arr3@Array(_, _, _) => arr3
      case arr2@Array(_, _) => 0 +: arr2
      case arr1@Array(_) => 0 +: 0 +: arr1
    }
    parts.map(p => f"$p%02d").mkString(":")
  }

  def set(request: HttpServletRequest, activityOid: ObjectId, actionName: String, duration: String,
          description: Option[String]): Unit = {
    if (!duration.matches("(?:(?:\\d{1,2}\\:)?\\d{1,2}\\:)?\\d{1,2}"))
      throw new IllegalArgumentException(s"Bad duration format: '$duration'")
    val theActivity: DynDoc = ActivityApi.activityById(activityOid)
    val actionNames: Seq[String] = ActivityApi.allActions(theActivity).map(_.name[String])
    val actionIdx = actionNames.indexOf(actionName)

    val valuesToSet = description match {
      case None => Map(s"actions.$actionIdx.duration" -> formatDuration(duration))
      case Some(desc) => Map(s"actions.$actionIdx.duration" -> formatDuration(duration),
        s"actions.$actionIdx.description" -> desc)
    }
    val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid), Map("$set" -> valuesToSet))
    if (updateResult.getMatchedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    else {
      val theProcess: DynDoc = ActivityApi.parentProcess(activityOid)
      val processOid = theProcess._id[ObjectId]
      val topLevelBpmn = theProcess.bpmn_name[String]
      ProcessBpmnTraverse.scheduleBpmnElements(topLevelBpmn, processOid, request)
    }
    BWLogger.audit(getClass.getName, request.getMethod, s"Set duration of action '$actionName' to '$duration'", request)
  }

}
