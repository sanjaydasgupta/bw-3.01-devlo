package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import org.bson.types.ObjectId

class DocumentVersionDelete extends HttpServlet with HttpUtils with MailUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val documentOid = new ObjectId(parameters("document_id"))
      val timestamp = parameters("timestamp").toLong
      val updateResult = BWMongoDB3.document_master.updateOne(Map("_id" -> documentOid),
        Map("$pull" -> Map("versions" -> Map("timestamp" -> timestamp))))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      val deleteResult = BWMongoDB3.document_master.
        deleteOne(Map("$and" -> Seq(Map("$where" -> "this.versions.length == 0"), Map("_id" -> documentOid))))
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doPost",
        s"EXIT-OK (deleted ${updateResult.getModifiedCount} versions, ${deleteResult.getDeletedCount} classes)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
