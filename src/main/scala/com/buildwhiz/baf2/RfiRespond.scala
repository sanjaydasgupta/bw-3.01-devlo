package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class RfiRespond extends HttpServlet with HttpUtils with DateTimeUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val timestamp = System.currentTimeMillis
      val user: DynDoc = getUser(request)
      val senderOid = user._id[ObjectId]
      val messageText = parameters("message")
      val rfiOid = new ObjectId(parameters("rfi_id"))
      val rfiRecord = RfiApi.rfiById(rfiOid)
      val projectOid = rfiRecord.project_id[ObjectId]

      val attachments = if (request.getContentType.contains("multipart") && request.getParts.size == 1) {
        val part = request.getParts.iterator.next()
        val submittedFilename = part.getSubmittedFileName
        val fullFileName = if (submittedFilename == null || submittedFilename.isEmpty)
          "unknown.tmp"
        else
          submittedFilename
        val fileType = if (fullFileName.contains(".")) fullFileName.split("\\.").last.trim else "tmp"
        val docOid = DocumentApi.createProjectDocumentRecord(f"RFI-Attachment-$timestamp%x",
          "", fileType, Seq.empty[String], projectOid, None, None, Some("SYSTEM"))
        val inputStream = part.getInputStream
        val storageResult = DocumentApi.storeAmazonS3(fullFileName, inputStream, projectOid.toString,
          docOid, timestamp, "-", senderOid, request)
        Seq(new Document("file_name", fullFileName).append("document_id", docOid).append("timestamp", timestamp))
      } else if (request.getContentType.contains("multipart") && request.getParts.size > 1) {
        throw new IllegalArgumentException(s"Multiple attachments not supported")
      } else {
        Seq.empty[Document]
      }

      val message = Map("text" -> messageText, "sender" -> user._id[ObjectId],
        "read_person_ids" -> Seq.empty[ObjectId], "attachments" -> attachments, "timestamp" -> timestamp)
      val updateResult = BWMongoDB3.rfi_messages.updateOne(Map("_id" -> rfiOid), Map("$push" -> Map("messages" -> message),
        "$set" -> Map("status" -> "active")))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB error: $updateResult")

      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
