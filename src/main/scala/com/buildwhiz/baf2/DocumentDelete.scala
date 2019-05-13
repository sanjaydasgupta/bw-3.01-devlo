package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class DocumentDelete extends HttpServlet with HttpUtils with MailUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {

      val documentOid = new ObjectId(parameters("document_id"))
      if (!DocumentApi.exists(documentOid))
        throw new IllegalArgumentException(s"unknown document_id: ${documentOid.toString}")

      val docRecord = DocumentApi.documentById(documentOid)
      val documentName = docRecord.name[String]

      val updateResult = BWMongoDB3.document_master.deleteOne(Map("_id" -> documentOid))
      if (updateResult.getDeletedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

      val message = s"Deleted document '$documentName'"

      // ToDo: delete documents from S3

      BWLogger.audit(getClass.getName, request.getMethod, message, request)
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
