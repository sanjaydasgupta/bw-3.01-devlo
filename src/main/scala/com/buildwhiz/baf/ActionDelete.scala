package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

class ActionDelete extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val activityOid = new ObjectId(parameters("activity_id"))
      val actionName = parameters("action_name")
      ActionDelete.delete(request, response, activityOid, actionName)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object ActionDelete {
  def delete(request: HttpServletRequest, response: HttpServletResponse, activityOid: ObjectId,
         actionName: String): Unit = {
    val theActivity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).head
    val actions: Seq[DynDoc] = theActivity.actions[Many[Document]]
    val actionIdx = actions.indexWhere(_.name[String] == actionName)
    if (actionIdx == -1)
      throw new IllegalArgumentException(s"Nonexistent action: '$actionName'")
    val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
      Map("$pull" -> Map("actions" -> Map("name" -> actionName))))
    if (updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    else {
      val thePhase: DynDoc = BWMongoDB3.phases.find(Map("activity_ids" -> activityOid)).head
      val topLevelBpmn = thePhase.bpmn_name[String]
      PhaseBpmnTraverse.scheduleBpmnElements(topLevelBpmn, thePhase._id[ObjectId], request, response)
    }
    BWLogger.audit(getClass.getName, "handlePost", s"Deleted action '$actionName'", request)
  }
}
