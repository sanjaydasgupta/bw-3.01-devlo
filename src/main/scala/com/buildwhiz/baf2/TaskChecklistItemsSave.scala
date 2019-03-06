package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class TaskChecklistItemsSave extends HttpServlet with HttpUtils with DateTimeUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val postData: DynDoc = Document.parse(getStreamData(request))

      val activityOid = new ObjectId(postData.activity_id[String])
      val newChecklistValues: Seq[(String, Boolean)] = postData.checklist_values[Many[Document]].
          map(nameStatus => (nameStatus.name[String], nameStatus.status[Boolean]))

      val theActivity = ActivityApi.activityById(activityOid)
      val oldChecklistValues: Seq[(String, Boolean)] = if (theActivity.has("check_list")) {
        theActivity.check_list[Many[Document]].
            map(nameStatus => (nameStatus.name[String], nameStatus.status[Boolean]))
      } else {
        Seq.empty[(String, Boolean)]
      }

      val checklistItemNames: Set[String] = oldChecklistValues.map(_._1).toSet
      val badChecklistItemNames = newChecklistValues.map(_._1).filterNot(name => checklistItemNames.contains(name))
      if (badChecklistItemNames.nonEmpty)
        throw new IllegalArgumentException(s"""Bad checklist item-name(s): ${badChecklistItemNames.mkString(", ")}""")

      val newValuesMap = newChecklistValues.toMap

      val modifiedArray: Seq[(String, Boolean, Boolean)] = oldChecklistValues.map(nameAndStatus => {
        val (oldName, oldStatus) = nameAndStatus
        val newStatus = newValuesMap.getOrElse(oldName, oldStatus)
        if (newStatus != oldStatus) {
          (oldName, newStatus, true)
        } else {
          (oldName, oldStatus, false)
        }
      })

      val modifications = modifiedArray.filter(_._3)
      if (modifications.nonEmpty) {
        val user: DynDoc = getUser(request)
        val message = modifications.map(t => s"""'${t._1}' to ${t._2}""").mkString("Set checklist item ", ", ", "")
        ActivityApi.addChangeLogEntry(activityOid, message, Some(user._id[ObjectId]), None)

        val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
          Map("$set" -> Map(s"check_list" -> modifiedArray.
            map(nameStatus => Map("name" -> nameStatus._1, "status" -> nameStatus._2)))))
        if (updateResult.getModifiedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

        BWLogger.audit(getClass.getName, request.getMethod, message, request)
      } else {
        BWLogger.log(getClass.getName, request.getMethod, "EXIT (OK-No-Change)", request)
      }
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}