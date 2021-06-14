package com.buildwhiz.baf3

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import org.bson.types.ObjectId
import com.buildwhiz.baf2.{DocumentApi, PersonApi, ProjectApi}

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class DocumentDelete extends HttpServlet with HttpUtils with MailUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val documentOid = new ObjectId(parameters("document_id"))
      val docRecord = DocumentApi.documentById(documentOid)
      val optDocProjectOid = docRecord.get[ObjectId]("project_id")

      val user: DynDoc = getPersona(request)
      optDocProjectOid match {
        case None =>
          throw new IllegalArgumentException(s"Not permitted")
        case Some(docProjOid) =>
          if (!ProjectApi.canManage(user._id[ObjectId], ProjectApi.projectById(docProjOid)) &&
              !PersonApi.isBuildWhizAdmin(Right(user)))
            throw new IllegalArgumentException(s"Not permitted")
      }

      val updateResult = BWMongoDB3.document_master.deleteOne(Map("_id" -> documentOid))
      if (updateResult.getDeletedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

      val documentName = docRecord.name[String]
      val message = s"Deleted document '$documentName'"

      // ToDo: delete document from Google-Drive

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
