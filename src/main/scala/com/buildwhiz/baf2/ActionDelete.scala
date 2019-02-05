package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class ActionDelete extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val activityOid = new ObjectId(parameters("activity_id"))
      val actionName = parameters("action_name")
      ActionDelete.delete(request, activityOid, actionName)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object ActionDelete {
  def delete(request: HttpServletRequest, activityOid: ObjectId, actionName: String): Unit = {
    val theActivity: DynDoc = ActivityApi.activityById(activityOid)
    val actions: Seq[DynDoc] = ActivityApi.allActions(theActivity)
    val actionIdx = actions.indexWhere(_.name[String] == actionName)
    if (actionIdx == -1)
      throw new IllegalArgumentException(s"Nonexistent action: '$actionName'")
    val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
      Map("$pull" -> Map("actions" -> Map("name" -> actionName))))
    if (updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    val theProcess: DynDoc = ActivityApi.parentProcess(theActivity._id[ObjectId])
    val topLevelBpmn = theProcess.bpmn_name[String]
    ProcessBpmnTraverse.scheduleBpmnElements(topLevelBpmn, theProcess._id[ObjectId], request)
    BWMongoDB3.document_master.deleteMany(Map("activity_id" -> activityOid, "action_name" -> actionName))
    BWLogger.audit(getClass.getName, "delete", s"Deleted action '$actionName'", request)
  }
}
