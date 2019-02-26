package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId
import org.bson.Document

class TaskChecklistItemAdd extends HttpServlet with HttpUtils with DateTimeUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val activityOid = new ObjectId(parameters("activity_id"))
      val theActivity = ActivityApi.activityById(activityOid)
      val checkListItemName = parameters("checklist_item_name")
      if (theActivity.has("check_list") &&
          theActivity.check_list[Many[Document]].exists(_.name[String] == checkListItemName))
        throw new IllegalArgumentException(s"Check list item already exists")
      val checkListItem = Map("name" -> checkListItemName, "status" -> false)
      val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
          Map("$push" -> Map("check_list" -> checkListItem)))
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