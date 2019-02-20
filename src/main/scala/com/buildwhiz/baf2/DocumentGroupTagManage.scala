package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class DocumentGroupTagManage extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val postDataStream = getStreamData(request)
      val tagParameters: DynDoc = if (postDataStream.nonEmpty) Document.parse(postDataStream) else new Document()
      val documentOids: Seq[ObjectId] = tagParameters.document_ids[Many[String]].map(id => new ObjectId(id))
      val tagNames: Seq[String] = tagParameters.tag_names[Many[String]]
      val operation = tagParameters.operation[String]
      if (!operation.matches("add|remove"))
        throw new IllegalArgumentException(s"Bad operation: '$operation'")
      val tagType = tagParameters.tag_type[String]
      if (tagType == "user") {
        val user: DynDoc = getUser(request)
        val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
        PersonApi.documentGroupManageLabels(freshUserRecord, documentOids, tagNames, operation)
      } else if (tagType == "system") {
        val anyDocument = DocumentApi.documentById(documentOids.head)
        val project = ProjectApi.projectById(anyDocument.project_id[ObjectId])
        ProjectApi.documentGroupManageLabels(project, documentOids, tagNames, operation)
      } else
        throw new IllegalArgumentException(s"Bad label-type: '$tagType'")
      val message = s"performed $operation document(s): '${documentOids.mkString(",")}' label(s): '${tagNames.mkString(",")}'"
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}
