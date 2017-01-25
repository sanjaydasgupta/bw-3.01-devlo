package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import com.buildwhiz.{HttpUtils, MailUtils}
import org.bson.types.ObjectId
import org.bson.Document

import scala.collection.JavaConverters._

class RFIMessageSubmit extends HttpServlet with HttpUtils with MailUtils {

  private def sendMail(members: Seq[ObjectId]): Unit = {
    //BWLogger.log(getClass.getName, "saveAndSendMail()", "ENTRY")
    try {
      //sendMail(recipientPersonOid, subject, message)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "saveAndSendMail()", s"ERROR ${t.getClass.getName}(${t.getMessage})")
    }
    //BWLogger.log(getClass.getName, "saveAndSendMail()", "EXIT-OK")
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val timestamp = System.currentTimeMillis
      val senderOid = new ObjectId(parameters("person_id"))
      val text = parameters("text")
      val message = if (parameters.contains("attachments")) {
        val attachments: Seq[Document] = parameters("attachments").split("#").
          map(a => {val d = Document.parse(a); if (d.containsKey("$$hashKey")) d.remove("$$hashKey"); d}).toSeq
        new Document(Map("text" -> text, "timestamp" -> timestamp, "sender" -> senderOid,
          "attachments" -> attachments, "read_person_ids" -> Nil))
      } else {
        new Document(Map("text" -> text, "timestamp" -> timestamp, "sender" -> senderOid,
          "read_person_ids" -> Nil))
      }
      if (parameters.contains("rfi_id")) {
        val rfiOid = new ObjectId(parameters("rfi_id"))
        BWMongoDB3.rfi_messages.updateOne(Map("_id" -> rfiOid), Map("$push" -> Map("messages" -> message),
            "$set" -> Map("status" -> "active")))
        val rfiMessage: DynDoc = BWMongoDB3.rfi_messages.find(Map("_id" -> rfiOid)).asScala.head
        val members: Seq[ObjectId] = rfiMessage.members[ObjectIdList].asScala
        sendMail(members)
      } else {
        val subject = parameters("subject")
        val projectOid = project430ForestOid //new ObjectId(parameters("project_id"))
        val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).asScala.head
        val projectManagersOid = project.admin_person_id[ObjectId]
        val docVersionTimestamp = parameters("doc_version_timestamp").toLong
        val documentOid = new ObjectId(parameters("document_id"))
        val docRecord: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).asScala.head
        val versions: Seq[DynDoc] = docRecord.versions[DocumentList]
        val documentTimestamp = parameters("doc_version_timestamp").toLong
        val authorOid = versions.filter(_.timestamp[Long] == documentTimestamp).head.author_person_id[ObjectId]
        val memberOids = Seq(senderOid, projectManagersOid, authorOid).distinct
        val newRfiObject = new Document(Map("members" -> memberOids, "subject" -> subject,
          "status" -> "new", "project_id" -> projectOid, "messages" -> Seq(message),
          "document" -> Map("document_id" -> documentOid, "version" -> docVersionTimestamp),
          "timestamps" -> Map("start" -> System.currentTimeMillis)))
        BWMongoDB3.rfi_messages.insertOne(newRfiObject)
        val idx: Int = versions.zipWithIndex.find(_._1.timestamp[Long] == docVersionTimestamp).head._2
        BWMongoDB3.document_master.updateOne(Map("_id" -> documentOid),
            Map("$push" -> Map(s"versions.$idx.rfi_ids" -> newRfiObject.get("_id"))))
        sendMail(memberOids)
      }
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doPost", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
