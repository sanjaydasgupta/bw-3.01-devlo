package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import com.buildwhiz.{HttpUtils, MailUtils}
import org.bson.types.ObjectId
import org.bson.Document

import scala.collection.JavaConverters._

class RFIMessageSubmit extends HttpServlet with HttpUtils with MailUtils {

  private def messageBody(subject: String) =
    s"""An RFI message has been posted with the following subject:
      |
      |    '$subject'
      |
      |This email was sent as you are either a manager or an author.""".stripMargin

  private def sendMail(members: Seq[ObjectId], subject: String): Unit = {
    BWLogger.log(getClass.getName, s"sendMail($members)", "ENTRY")
    try {
      sendMail(members, s"RFI for '$subject'", messageBody(subject))
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "sendMail()", s"ERROR ${t.getClass.getName}(${t.getMessage})")
    }
    BWLogger.log(getClass.getName, "sendMail()", "EXIT-OK")
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val postData: DynDoc = Document.parse(getStreamData(request))
      val timestamp = System.currentTimeMillis
      val senderOid = new ObjectId(postData.person_id[String])
      val text = postData.text[String]
      val message = if (postData.has("attachments")) {
        val attachments: Seq[Document] = postData.attachments[String].split("#").
          map(a => {val d = Document.parse(a); if (d.containsKey("$$hashKey")) d.remove("$$hashKey"); d}).toSeq
        new Document(Map("text" -> text, "timestamp" -> timestamp, "sender" -> senderOid,
          "attachments" -> attachments, "read_person_ids" -> Nil))
      } else {
        new Document(Map("text" -> text, "timestamp" -> timestamp, "sender" -> senderOid,
          "read_person_ids" -> Nil))
      }
      if (postData.has("rfi_id")) {
        val rfiOid = new ObjectId(postData.rfi_id[String])
        BWMongoDB3.rfi_messages.updateOne(Map("_id" -> rfiOid), Map("$push" -> Map("messages" -> message),
            "$set" -> Map("status" -> "active")))
        val rfiMessage: DynDoc = BWMongoDB3.rfi_messages.find(Map("_id" -> rfiOid)).asScala.head
        val members: Seq[ObjectId] = rfiMessage.members[ObjectIdList].asScala
        val subject: String = rfiMessage.subject[String]
        sendMail(members.filterNot(_ == senderOid), subject)
      } else {
        val subject = postData.subject[String]
        val projectOid = project430ForestOid //new ObjectId(parameters("project_id"))
        val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).asScala.head
        val projectManagersOid = project.admin_person_id[ObjectId]
        val docVersionTimestamp = postData.doc_version_timestamp[String].toLong
        val documentOid = new ObjectId(postData.document_id[String])
        val docRecord: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).asScala.head
        val versions: Seq[DynDoc] = docRecord.versions[DocumentList]
        val documentTimestamp = postData.doc_version_timestamp[String].toLong
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
        sendMail(memberOids.filterNot(_ == senderOid), subject)
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
