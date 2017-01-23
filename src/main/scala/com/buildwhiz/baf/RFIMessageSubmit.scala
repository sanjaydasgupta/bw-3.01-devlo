package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import com.buildwhiz.{HttpUtils, MailUtils}
import org.bson.types.ObjectId
import org.bson.Document

import scala.collection.JavaConverters._

class RFIMessageSubmit extends HttpServlet with HttpUtils with MailUtils {

  private def saveAndSendMail(projectOid: ObjectId, activityOid: ObjectId, action: DynDoc, isRequest: Boolean): Unit = {
    BWLogger.log(getClass.getName, "saveAndSendMail()", "ENTRY")
    try {
      val reqOrResp = if (isRequest) "request" else "response"
      val subject = s"RFI $reqOrResp received"
      val message = s"You have a RFI $reqOrResp for action '${action.name[String]}'"
      val recipientPersonOid: ObjectId = if (isRequest) {
        val phase: DynDoc = BWMongoDB3.phases.find(Map("activity_ids" -> activityOid)).asScala.head
        phase.admin_person_id[ObjectId]
      } else {
        action.assignee_person_id[ObjectId]
      }
      BWMongoDB3.mails.insertOne(Map("project_id" -> projectOid, "timestamp" -> System.currentTimeMillis,
        "recipient_person_id" -> recipientPersonOid, "subject" -> subject, "message" -> message))
      sendMail(recipientPersonOid, subject, message)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "saveAndSendMail()", s"ERROR ${t.getClass.getName}(${t.getMessage})")
    }
    BWLogger.log(getClass.getName, "saveAndSendMail()", "EXIT-OK")
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val timestamp = System.currentTimeMillis
      val senderOid = new ObjectId(parameters("person_id"))
      val text = parameters("text")
      val message = new Document(Map("text" -> text, "timestamp" -> timestamp, "sender" -> senderOid,
          "read_person_ids" -> Seq.empty[ObjectId]))
      if (parameters.contains("rfi_id")) {
        val rfiOid = new ObjectId(parameters("rfi_id"))
        BWMongoDB3.rfi_messages.updateOne(Map("_id" -> rfiOid), Map("$push" -> Map("messages" -> message),
            "$set" -> Map("status" -> "active")))
      } else {
        val subject = parameters("subject")
        val projectOid = project430ForestOid //new ObjectId(parameters("project_id"))
        val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).asScala.head
        val projectManagersOid = project.admin_person_id[ObjectId]
        val members = Seq(senderOid, projectManagersOid)
        val docVersionTimestamp = parameters("doc_version_timestamp").toLong
        val documentOid = new ObjectId(parameters("document_id"))
        val newRfiObject = new Document(Map("members" -> members, "subject" -> subject,
          "status" -> "new", "project_id" -> projectOid, "messages" -> Seq(message),
          "document" -> Map("document_id" -> documentOid, "version" -> docVersionTimestamp)))
        BWMongoDB3.rfi_messages.insertOne(newRfiObject)
        val docRecord: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).asScala.head
        val versions: Seq[DynDoc] = docRecord.versions[DocumentList]
        val versionIdx: Int = versions.zipWithIndex.find(_._1.timestamp[Long] == docVersionTimestamp).head._2
        BWMongoDB3.document_master.updateOne(Map("_id" -> documentOid),
            Map("$push" -> Map(s"versions.$versionIdx.rfi_ids" -> newRfiObject.get("_id"))))
      }
      //saveAndSendMail(projectOid, activityOid, theAction, isRequest)
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
