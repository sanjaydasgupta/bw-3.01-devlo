package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class RfiDetails extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  private def getAttachments(message: DynDoc): Seq[Document] = {
    val attachments: Seq[DynDoc] = if (message.has("attachments")) message.attachments[Many[Document]] else Nil
    for (attachment <- attachments) {
      val fileName = attachment.file_name[String]
      val docOid = attachment.document_id[ObjectId]
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
      val rfiExchange: DynDoc = RfiApi.rfiById(rfiOid)
      val messages: Seq[DynDoc] = rfiExchange.messages[Many[Document]]
      val user: DynDoc = getUser(request)
      val messageLines: Seq[Document] = messages.sortBy(m => -m.timestamp[Long]).map(message => {
        val sender: DynDoc = BWMongoDB3.persons.find(Map("_id" -> message.sender[ObjectId])).head
        val senderName = PersonApi.fullName(sender)
        val clientTimezone = user.tz[String]
        new Document(Map("timestamp" -> dateTimeString(message.timestamp[Long], Some(clientTimezone)),
          "text" -> (if (message.text[Any] == null) "" else message.text[String]),
          "sender" -> senderName, "attachments" -> getAttachments(message)))
      })
      for (idx <- messages.indices) {
        BWMongoDB3.rfi_messages.updateOne(Map("_id" -> rfiOid),
          Map("$addToSet" -> Map(s"messages.$idx.read_person_ids" -> user._id[ObjectId])))
      }
      val results = messageLines.map(bson2json).mkString("[", ", ", "]")
      response.getWriter.print(results)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${messageLines.length} objects)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
