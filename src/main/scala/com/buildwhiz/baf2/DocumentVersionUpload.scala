package com.buildwhiz.baf2

import java.util.{Calendar, TimeZone}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class DocumentVersionUpload extends HttpServlet with HttpUtils with MailUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val documentOid = new ObjectId(parameters("document_id"))
      if (!DocumentApi.exists(documentOid))
        throw new IllegalArgumentException(s"unknown document-id: ${documentOid.toString}")

      val user: DynDoc = getUser(request)
      val authorOid = parameters.get("author_id") match {
        case Some(aId) => new ObjectId(aId)
        case None => user._id[ObjectId]
      }
      if (!PersonApi.exists(authorOid))
        throw new IllegalArgumentException(s"unknown author-id: ${authorOid.toString}")

      if (request.getParts.size != 1)
        throw new IllegalArgumentException(s"parts.length != 1")
      val part = request.getParts.iterator.next()
      //val uploadSize = part.getSize
      //if (uploadSize > 1e7)
      //  throw new IllegalArgumentException(s"attachment size > 10Mb")
      val submittedFilename = part.getSubmittedFileName
      val fullFileName = if (submittedFilename == null || submittedFilename.isEmpty)
        "unknown.tmp"
      else
        submittedFilename

      val timestamp = parameters.get("timestamp") match {
        case Some(ts) =>
          val userTimezone = TimeZone.getTimeZone(user.tz[String])
          val calendar = Calendar.getInstance(userTimezone)
          val timezoneOffset = userTimezone.getOffset(calendar.getTimeInMillis)
          ts.toLong - timezoneOffset
        case None => System.currentTimeMillis
      }

      val documentRecord: DynDoc = DocumentApi.documentById(documentOid)
      val projectOid = documentRecord.project_id[ObjectId]

      val inputStream = part.getInputStream

      val versionComments = if (parameters.contains("comments")) parameters("comments") else "-"

      //val storageResults = DocumentApi.storeAmazonS3(fullFileName, inputStream, projectOid.toString,
      //  documentOid, timestamp, versionComments, authorOid, request)
      val project = ProjectApi.projectById(projectOid)
      val projectName = project.name[String]
      val documentName = documentRecord.name[String]
      val systemTags = documentRecord.labels[Many[String]].mkString(",")
      val properties = Map("project" -> projectName, "name" -> documentName, "tags" -> systemTags)
      val storageResults = DocumentApi.storeDocument(fullFileName, inputStream, projectOid.toString,
        documentOid, timestamp, versionComments, authorOid, properties, request)

      response.setStatus(HttpServletResponse.SC_OK)
      val message = s"Added version (${storageResults._2} bytes) to file ${documentRecord.name[String]}"
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
