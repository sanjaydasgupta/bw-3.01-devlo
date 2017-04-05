package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils, MailUtils}
import org.bson.types.ObjectId
import org.bson.Document

import scala.collection.JavaConverters._

class RFIMessageSubmit extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  private def messageBody(subject: String, uri: String) =
    s"""${dateTimeString(System.currentTimeMillis)}
      |An RFI message has been posted with the following subject:
      |
      |&nbsp;&nbsp;&nbsp;&nbsp;<a href="$uri">$subject</a>&nbsp;&nbsp;(Click link to see details)
      |
      |This email was sent as you are either a manager or an author.""".stripMargin

  private def sendRFIMail(members: Seq[ObjectId], subject: String, uri: String): Unit = {
    BWLogger.log(getClass.getName, s"sendMail($members)", "ENTRY")
    try {
      sendMail(members, s"RFI for '$subject'", messageBody(subject, uri))
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
        val rfiMessage: DynDoc = BWMongoDB3.rfi_messages.find(Map("_id" -> rfiOid)).head
        val members: Seq[ObjectId] = rfiMessage.members[Many[ObjectId]]
        val subject: String = rfiMessage.subject[String]
        val url = request.getRequestURL.toString.split("/").reverse.drop(2).reverse.mkString("/") +
          s"/#/rfi?rfi_id=$rfiOid"
        sendRFIMail(members.filterNot(_ == senderOid), subject, url)
      } else {
        val subject = postData.subject[String]
        val projectOid = project430ForestOid //new ObjectId(parameters("project_id"))
        val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
        val projectManagersOid = project.admin_person_id[ObjectId]
        val documentOid = new ObjectId(postData.document_id[String])
        val docRecord: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).head
        val versions: Seq[DynDoc] = docRecord.versions[Many[Document]]
        val documentTimestamp = postData.doc_version_timestamp[String].toLong
        val authorOid = versions.filter(_.timestamp[Long] == documentTimestamp).head.author_person_id[ObjectId]
        val memberOids = Seq(senderOid, projectManagersOid, authorOid).distinct
        val newRfiObject = new Document(Map("members" -> memberOids, "subject" -> subject,
          "status" -> "new", "project_id" -> projectOid, "messages" -> Seq(message),
          "document" -> Map("document_id" -> documentOid, "version" -> documentTimestamp),
          "timestamps" -> Map("start" -> System.currentTimeMillis)))
        BWMongoDB3.rfi_messages.insertOne(newRfiObject)
        val idx: Int = versions.zipWithIndex.find(_._1.timestamp[Long] == documentTimestamp).head._2
        val rfiOid = newRfiObject.get("_id")
        BWMongoDB3.document_master.updateOne(Map("_id" -> documentOid),
            Map("$push" -> Map(s"versions.$idx.rfi_ids" -> rfiOid)))
        val url = request.getRequestURL.toString.split("/").reverse.drop(2).reverse.mkString("/") +
          s"/#/rfi?rfi_id=$rfiOid"
        sendRFIMail(memberOids.filterNot(_ == senderOid), subject, url)
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
