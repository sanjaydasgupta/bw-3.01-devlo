package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse, Part}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class RfiStart extends HttpServlet with HttpUtils with MailUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val documentOid = new ObjectId(parameters("document_id"))
      if (!DocumentApi.exists(documentOid))
        throw new IllegalArgumentException(s"Bad document-id: '$documentOid'")
      val documentTimestamp = parameters("doc_version_timestamp").toLong
      val projectOid = new ObjectId(parameters("project_id"))
      if (!ProjectApi.exists(projectOid))
        throw new IllegalArgumentException(s"Bad project-id: '$projectOid'")
      val priority = parameters("priority")
      val question = parameters("question")
      val messageText = parameters("message")
      val subject = parameters("subject")
      val recipientRoles = parameters("recipient_roles").split(",").map(_.trim).toSeq
      val millisNow = System.currentTimeMillis
      val parts: Seq[Part] = request.getContentType match {
        case null => Seq.empty[Part]
        case s if s.startsWith("multipart/form-data") => request.getParts.asScala.toSeq
        case _ => Seq.empty[Part]
      }
      val attachments = parts.zipWithIndex.map(part => {
        val submittedFilename = part._1.getSubmittedFileName
        val fullFileName = if (submittedFilename == null || submittedFilename.isEmpty)
          "unknown.tmp"
        else
          submittedFilename
        val fileType = if (fullFileName.contains(".")) fullFileName.split("\\.").last.trim else "tmp"
        val docOid = DocumentApi.createProjectDocumentRecord(f"RFI-Attachment-$millisNow%x-${part._2}",
          "", fileType, Seq.empty[String], projectOid, None, None, Some("SYSTEM"))
        val inputStream = part._1.getInputStream
        val storageResult = DocumentApi.storeAmazonS3(fullFileName, inputStream, projectOid.toString,
          docOid, millisNow, "-", user._id[ObjectId], request)
        new Document("file_name", fullFileName).append("document_id", docOid).append("timestamp", millisNow)
      })

      val message = Map("text" -> messageText, "sender" -> user._id[ObjectId],
        "read_person_ids" -> Seq.empty[ObjectId], "attachments" -> attachments, "timestamp" -> millisNow)

      BWMongoDB3.rfi_messages.insertOne(Map("timestamps" -> Map("start" -> millisNow), "subject" -> subject,
        "status" -> "open", "question" -> question, "project_id" -> projectOid, "priority" -> priority,
        "document" -> Map("document_id" -> documentOid, "version" -> documentTimestamp),
        "recipient_roles" -> recipientRoles, "messages" -> Seq(message)
        ))
      //saveAndSendMail(projectOid, activityOid, theAction, isRequest, request)
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doPost", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}
