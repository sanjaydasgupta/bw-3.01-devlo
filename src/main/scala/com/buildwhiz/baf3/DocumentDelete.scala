package com.buildwhiz.baf3

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import org.bson.types.ObjectId
import com.buildwhiz.baf2.{DocumentApi, PersonApi, ProjectApi}

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class DocumentDelete extends HttpServlet with HttpUtils with MailUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val optDocumentOid = parameters.get("document_id").map(did => new ObjectId(did))
      val optDeliverableOid = parameters.get("deliverable_id").map(did => new ObjectId(did))
      val (mongoQuery, optProjectOid) = (optDocumentOid, optDeliverableOid) match {
        case (Some(documentOid), None) =>
          (Map("_id" -> documentOid), Some(DocumentApi.documentById(documentOid).project_id[ObjectId]))
        case (None, Some(deliverableOid)) =>
          val dbQuery = Map("deliverable_id" -> deliverableOid)
          val optProjectOid = BWMongoDB3.document_master.find(dbQuery).headOption.map(_.project_id[ObjectId])
          (dbQuery, optProjectOid)
        case _ => throw new IllegalArgumentException("Found both 'document_id' and 'deliverable_id'")
      }

      val user: DynDoc = getPersona(request)
      val logMessage = optProjectOid match {
        case Some(projectOid) =>
          if (!ProjectApi.canManage(user._id[ObjectId], ProjectApi.projectById(projectOid)) &&
              !PersonApi.isBuildWhizAdmin(Right(user)))
            throw new IllegalArgumentException(s"Not permitted")
          val updateResult = BWMongoDB3.document_master.deleteOne(mongoQuery)
          if (updateResult.getDeletedCount == 0)
            throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
          s"Deleted ${updateResult.getDeletedCount} document(s) by '$mongoQuery'"
        case _ => // do nothing
          s"Deleted NO documents by '$mongoQuery'"
      }
      BWLogger.audit(getClass.getName, request.getMethod, logMessage, request)

      // ToDo: delete document from Google-Drive

      response.getWriter.print(successJson())
      response.setContentType("application/json")
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
