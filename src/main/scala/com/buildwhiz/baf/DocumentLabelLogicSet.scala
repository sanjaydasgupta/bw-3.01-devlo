package com.buildwhiz.baf

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class DocumentLabelLogicSet extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val labelName: String = parameters("label_name")
      val logic: String = parameters("logic")
      val user: DynDoc = getUser(request)
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      val userLabels: Seq[DynDoc] = if (freshUserRecord.has("labels"))
        freshUserRecord.labels[Many[Document]] else Seq.empty[Document]
      if (!userLabels.exists(_.name[String] == labelName))
        throw new IllegalArgumentException(s"label '$labelName' not found")
      val idx = userLabels.indexWhere(_.name[String] == labelName)
      val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> user._id[ObjectId]),
        Map("$set" -> Map(s"labels.$idx.logic" -> logic)))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      //
      //  ToDo Evaluate applicable document list
      //
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.audit(getClass.getName, request.getMethod, s"Added logic '$logic' to label '$labelName'", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}
