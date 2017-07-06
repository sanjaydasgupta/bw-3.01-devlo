package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

class ActionDurationSet extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    def formatDuration(d: String): String = {
      val parts = d.split(":").map(_.toInt) match {
        case arr3@Array(_, _, _) => arr3
        case arr2@Array(_, _) => 0 +: arr2
        case arr1@Array(_) => 0 +: 0 +: arr1
      }
      parts.map(p => f"$p%02d").mkString(":")
    }
    try {
      val duration = parameters("duration")
      if (!duration.matches("(?:(?:\\d{1,2}\\:)?\\d{1,2}\\:)?\\d{1,2}"))
        throw new IllegalArgumentException("Bad duration format")
      val activityOid = new ObjectId(parameters("activity_id"))
      val theActivity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).head
      val actionNames: Seq[String] = theActivity.actions[Many[Document]].map(_.name[String])
      val actionName = parameters("action_name")
      val actionIdx = actionNames.indexOf(actionName)
      val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
        Map("$set" -> Map(s"actions.$actionIdx.duration" -> formatDuration(duration))))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      else {
        val bpmnName = theActivity.bpmn_name[String]
        val thePhase: DynDoc = BWMongoDB3.phases.find(Map("activity_ids" -> activityOid)).head
        val phaseOid = thePhase._id[ObjectId]
        val topLevelBpmn = thePhase.bpmn_name[String]
        PhaseBpmnTraverse.scheduleBpmnElements(topLevelBpmn, phaseOid, request, response)
      }
      response.setStatus(HttpServletResponse.SC_OK)
      val actionLog = s"'$actionName'"
      BWLogger.audit(getClass.getName, "doPost", s"""Set duration of action $actionLog""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
