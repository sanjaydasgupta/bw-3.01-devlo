package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class TaskChecklistItemToggle extends HttpServlet with HttpUtils with DateTimeUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val activityOid = new ObjectId(parameters("activity_id"))
      val checkListItemName = parameters("checklist_item_name")

      val theActivity = ActivityApi.activityById(activityOid)
      val checkList: Seq[DynDoc] = if (theActivity.has("check_list"))
          theActivity.check_list[Many[Document]]
      else
        Seq.empty[DynDoc]

      val idx = checkList.indexWhere(_.name[String] == checkListItemName)
      if (idx == -1)
        throw new IllegalArgumentException(s"Bad checklist item-name: '$checkListItemName'")

      val currentStatus = checkList(idx).status[Boolean]

      val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
          Map("$set" -> Map(s"check_list.$idx.status" -> !currentStatus)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}