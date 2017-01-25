package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.{DateTimeUtils, HttpUtils, MailUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class RFIDetailsFetch extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  private def getAttachments(message: DynDoc): Seq[Document] = {
    val attachments: Seq[DynDoc] = if (message.has("attachments")) message.attachments[DocumentList] else Nil
    for (attachment <- attachments) {
      val fileName = attachment.file_name[String]
      val docOid = attachment.document_id[String]
      val ts = attachment.timestamp[Long]
      attachment.link = s"baf/DocumentVersionDownload/$fileName?document_master_id=$docOid&timestamp=$ts"
    }
    attachments.map(_.asDoc)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val rfiOid = new ObjectId(parameters("rfi_id"))
      val rfiExchange: DynDoc = BWMongoDB3.rfi_messages.find(Map("_id" -> rfiOid)).asScala.head
      val messages: Seq[DynDoc] = rfiExchange.messages[DocumentList]
      val messageLines: Seq[Document] = messages.sortBy(m => -m.timestamp[Long]).map(message => {
        val sender: DynDoc = BWMongoDB3.persons.find(Map("_id" -> message.sender[ObjectId])).asScala.head
        val senderName = s"${sender.first_name[String]} ${sender.last_name[String]}"
        val clientTimezone = parameters("tz")
        new Document(Map("timestamp" -> dateTimeString(message.timestamp[Long], Some(clientTimezone)),
          "text" -> message.text[String], "sender" -> senderName, "attachments" -> getAttachments(message)))
      })
      val user: DynDoc = getUser(request)
      for (idx <- messages.indices) {
        BWMongoDB3.rfi_messages.updateOne(Map("_id" -> rfiOid),
          Map("$addToSet" -> Map(s"messages.$idx.read_person_ids" -> user._id[ObjectId])))
      }
      response.getWriter.print(messageLines.map(bson2json).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${messageLines.length} objects)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
