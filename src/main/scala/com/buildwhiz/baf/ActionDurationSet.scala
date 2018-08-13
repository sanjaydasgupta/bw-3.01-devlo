package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

class ActionDurationSet extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val actionName = parameters("action_name")
      val duration = parameters("duration")
      val description = parameters.get("description")
      val activityOid = new ObjectId(parameters("activity_id"))
      ActionDurationSet.set(request, activityOid, actionName, duration, description)
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
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
    val theActivity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).head
    val actionNames: Seq[String] = theActivity.actions[Many[Document]].map(_.name[String])
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
      val thePhase: DynDoc = BWMongoDB3.phases.find(Map("activity_ids" -> activityOid)).head
      val phaseOid = thePhase._id[ObjectId]
      val topLevelBpmn = thePhase.bpmn_name[String]
      PhaseBpmnTraverse.scheduleBpmnElements(topLevelBpmn, phaseOid, request)
    }
    BWLogger.audit(getClass.getName, "doPost", s"Set duration of action '$actionName' to '$duration'", request)
  }

}
