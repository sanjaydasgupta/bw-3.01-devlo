package com.buildwhiz.baf3

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils, MailUtils}
import org.bson.types.ObjectId
import com.buildwhiz.baf2.{DocumentApi, PersonApi, PhaseApi, ProjectApi}

import java.util.{Calendar, TimeZone}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class DocumentUpload extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val documentOid = new ObjectId(parameters("document_id"))
      if (!DocumentApi.exists(documentOid))
        throw new IllegalArgumentException(s"unknown document-id: $documentOid")

      val user: DynDoc = getUser(request)
      val (authorOid, authorName) = parameters.get("author_id") match {
        case Some(aId) =>
          if (!PersonApi.isBuildWhizAdmin(Right(user)))
            throw new IllegalArgumentException("Not permitted to provide author_id")
          val authorOid = new ObjectId(aId)
          (authorOid, PersonApi.fullName(PersonApi.personById(authorOid)))
        case None => val authorOid = user._id[ObjectId]
          (authorOid, PersonApi.fullName(user))
      }
      if (!PersonApi.exists(authorOid))
        throw new IllegalArgumentException(s"unknown author-id: ${authorOid.toString}")

      if (request.getParts.size != 1)
        throw new IllegalArgumentException(s"parts.length != 1")
      val part = request.getParts.iterator.next()
      val submittedFilename = part.getSubmittedFileName
      val fullFileName = if (submittedFilename == null || submittedFilename.isEmpty)
        "unknown.tmp"
      else
        submittedFilename

      val timestamp = parameters.get("timestamp") match {
        case Some(ts) =>
          if (!PersonApi.isBuildWhizAdmin(Right(user)))
            throw new IllegalArgumentException("Not permitted to provide timestamp")
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

      val project = ProjectApi.projectById(projectOid)
      val projectName = project.name[String]
      val phaseName = documentRecord.get[ObjectId]("phase_id") match {
        case None => null
        case Some(phaseOid) => PhaseApi.phaseById(phaseOid).name[String]
      }
      val documentName = documentRecord.name[String]
      val systemTags = documentRecord.get[Many[String]]("labels") match {
        case Some(labels) => labels.mkString(",")
        case None => ""
      }
      val properties = Map("project" -> projectName, "phase" -> phaseName, "name" -> documentName, "tags" -> systemTags,
          "author" -> authorName, "timestamp" -> dateTimeString(timestamp))
      val storageResults = DocumentApi.storeDocument(fullFileName, inputStream, projectOid.toString,
        documentOid, timestamp, versionComments, authorOid, properties, request)

      response.setStatus(HttpServletResponse.SC_OK)
      val message = s"Added version (${storageResults._2} bytes) to file ${documentRecord.name[String]}"
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
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
