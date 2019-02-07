package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class DocumentTagDelete extends HttpServlet with HttpUtils {

  private def deleteSystemTag(request: HttpServletRequest): Unit = {
  }

  private def deleteUserTag(request: HttpServletRequest): Unit = {
    val parameters = getParameterMap(request)
    val labelName: String = parameters("label_name")
    val user: DynDoc = getUser(request)
    val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
    val userLabels: Seq[DynDoc] = if (freshUserRecord.has("labels"))
      freshUserRecord.labels[Many[Document]] else Seq.empty[Document]
    if (!userLabels.exists(_.name[String] == labelName))
      throw new IllegalArgumentException(s"label '$labelName' not found")
    val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> user._id[ObjectId]),
      Map("$pull" -> Map("labels" -> Map("name" -> labelName))))
    if (updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    BWMongoDB3.persons.updateOne(Map("_id" -> user._id[ObjectId],
      "document_filter_labels" -> Map("$exists" -> true)),
      Map("$pull" -> Map("document_filter_labels" -> labelName)))
    BWLogger.audit(getClass.getName, "doPost", s"Deleted document-label '$labelName'", request)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val tagType = parameters("tag_type")
      if (tagType == "user") {
        deleteUserTag(request)
      } else if (tagType == "system") {
        deleteSystemTag(request)
      }
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}
