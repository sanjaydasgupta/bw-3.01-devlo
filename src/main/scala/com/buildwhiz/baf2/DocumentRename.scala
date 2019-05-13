package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class DocumentRename extends HttpServlet with HttpUtils with MailUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {

      val newName = parameters("new_name")
      if (newName.trim.length < 5)
        throw new IllegalArgumentException(s"New name too short: '$newName'")

      val documentOid = new ObjectId(parameters("document_id"))
      if (!DocumentApi.exists(documentOid))
        throw new IllegalArgumentException(s"unknown document_id-id: ${documentOid.toString}")

      val docRecord = DocumentApi.documentById(documentOid)
      val currentName = docRecord.name[String]

      if (currentName == newName)
        throw new IllegalArgumentException(s"New name same as existing name")

      val updateResult = BWMongoDB3.document_master.updateOne(Map("_id" -> documentOid),
        Map("$push" -> Map("previous_names" -> currentName), "$set" -> Map("name" -> newName)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

      val message = s"Renamed document '$currentName' to '$newName'"
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

