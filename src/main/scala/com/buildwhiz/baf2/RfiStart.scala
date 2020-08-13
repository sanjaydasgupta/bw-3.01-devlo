package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class RfiStart extends HttpServlet with HttpUtils with MailUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      if (!ProjectApi.exists(projectOid))
        throw new IllegalArgumentException(s"Bad project-id: '$projectOid'")

      val user: DynDoc = getUser(request)
      val millisNow = System.currentTimeMillis
      val parts = getParts(request)

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
        ///*val storageResult = */DocumentApi.storeAmazonS3(fullFileName, inputStream, projectOid.toString,
        val project = ProjectApi.projectById(projectOid)
        val projectName = project.name[String]
        val properties = Map("project" -> projectName)
        DocumentApi.storeDocument(fullFileName, inputStream, projectOid.toString,
          docOid, millisNow, "-", user._id[ObjectId], properties, request)
        new Document("file_name", fullFileName).append("document_id", docOid).append("timestamp", millisNow)
      })

      val rfiType = parameters.get("rfi_type") match {
        case None => "NA"
        case Some(theType) if theType.matches("RFI|Issue|Discussion") => theType
        case Some(other) =>
          throw new IllegalArgumentException(s"Bad type: '$other'")
      }
      val priority = parameters("priority")
      val question = parameters("question")
      val messageText = parameters("message")
      val subject = parameters("subject")
      val recipientRoles = parameters("recipient_roles").split(",").map(_.trim).toSeq

      val message = Map("text" -> messageText, "sender" -> user._id[ObjectId],
        "read_person_ids" -> Seq.empty[ObjectId], "attachments" -> attachments, "timestamp" -> millisNow)

      val rfiRecord = (parameters.get("document_id"), parameters.get("activity_id"), parameters.get("phase_id")) match {
        case (Some(documentId), _, _) =>
          val documentOid = new ObjectId(documentId)
          if (!DocumentApi.exists(documentOid))
            throw new IllegalArgumentException(s"Bad document-id: '$documentOid'")
          val documentTimestamp = parameters("doc_version_timestamp").toLong

          Map("rfi_type" -> rfiType, "timestamps" -> Map("start" -> millisNow), "subject" -> subject,
            "status" -> "open", "question" -> question, "project_id" -> projectOid, "priority" -> priority,
            "document" -> Map("id_type" -> "document_id", "document_id" -> documentOid, "version" -> documentTimestamp),
            "recipient_roles" -> recipientRoles, "messages" -> Seq(message))
        case (None, Some(activityId), _) =>
          val activityOid = new ObjectId(activityId)
          if (!ActivityApi.exists(activityOid))
            throw new IllegalArgumentException(s"Bad activity-id: '$activityOid'")
          Map("rfi_type" -> rfiType, "timestamps" -> Map("start" -> millisNow), "subject" -> subject,
            "status" -> "open", "question" -> question, "project_id" -> projectOid, "priority" -> priority,
            "document" -> Map("id_type" -> "activity_id", "activity_id" -> activityOid),
            "recipient_roles" -> recipientRoles, "messages" -> Seq(message))
        case (None, None, Some(phaseId)) =>
          val phaseOid = new ObjectId(phaseId)
          if (!PhaseApi.exists(phaseOid))
            throw new IllegalArgumentException(s"Bad phase-id: '$phaseOid'")
          Map("rfi_type" -> rfiType, "timestamps" -> Map("start" -> millisNow), "subject" -> subject,
            "status" -> "open", "question" -> question, "project_id" -> projectOid, "priority" -> priority,
            "document" -> Map("id_type" -> "phase_id", "phase_id" -> phaseOid),
            "recipient_roles" -> recipientRoles, "messages" -> Seq(message))
        case _ => throw new IllegalArgumentException(s"Mandatory parameters missing")
      }

      BWMongoDB3.rfi_messages.insertOne(rfiRecord)
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
