package com.buildwhiz.baf3

import com.buildwhiz.baf2.{DocumentApi, PersonApi, ProjectApi}
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class DocumentDeleteVersion extends HttpServlet with HttpUtils with MailUtils {

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

      val timestamp = parameters("timestamp").toLong

      val updateResult = BWMongoDB3.document_master.updateOne(Map("_id" -> documentOid),
          Map($pull -> Map("versions" -> Map("timestamp" -> timestamp))))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

      val documentName = docRecord.name[String]
      val message = s"Deleted version $timestamp of document '$documentName'"

      // ToDo: delete document from Google-Drive

      response.getWriter.print(successJson())
      response.setContentType("application/json")
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
