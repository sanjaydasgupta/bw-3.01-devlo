package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

class DocumentLabelManage extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val user: DynDoc = getUser(request)
      val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      val labelObjects: Seq[DynDoc] = if (person.has("labels")) person.labels[Many[Document]] else Seq.empty[Document]
      val labelName = parameters("label_name")
      val idx = labelObjects.indexWhere(_.name[String] == labelName)
      if (idx == -1)
        throw new IllegalArgumentException(s"label '$labelName' not found")
      val documentOid = new ObjectId(parameters("document_id"))
      val (modifiedCount, operation) = parameters("op") match {
        case "add" =>
          val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> user._id[ObjectId]),
            Map("$addToSet" -> Map(s"labels.$idx.document_ids" -> documentOid)))
          (updateResult.getModifiedCount, "Added")
        case "remove" =>
          val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> user._id[ObjectId]),
            Map("$pull" -> Map(s"labels.$idx.document_ids" -> documentOid)))
          (updateResult.getModifiedCount, "Removed")
        case op => throw new IllegalArgumentException(s"operation '$op' not recognized")
      }
      response.setStatus(HttpServletResponse.SC_OK)
      if (modifiedCount > 0)
        BWLogger.audit(getClass.getName, request.getMethod, s"$operation document '$documentOid' to label '$labelName'", request)
      else
        BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK (without changes)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}
