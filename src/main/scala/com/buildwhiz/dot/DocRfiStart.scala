package com.buildwhiz.dot

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class DocRfiStart extends HttpServlet with HttpUtils with MailUtils {

  private def sendMail(projectOid: ObjectId, activityOid: ObjectId, action: DynDoc, isRequest: Boolean,
        request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getName, "saveAndSendMail()", "ENTRY")
    try {
      val reqOrResp = if (isRequest) "request" else "response"
      val subject = s"RFI $reqOrResp received"
      val message = s"You have a RFI $reqOrResp for action '${action.name[String]}'"
      val recipientPersonOid: ObjectId = if (isRequest) {
        val phase: DynDoc = BWMongoDB3.phases.find(Map("activity_ids" -> activityOid)).head
        phase.admin_person_id[ObjectId]
      } else {
        action.assignee_person_id[ObjectId]
      }
      BWMongoDB3.mails.insertOne(Map("project_id" -> projectOid, "timestamp" -> System.currentTimeMillis,
        "recipient_person_id" -> recipientPersonOid, "subject" -> subject, "message" -> message))
      sendMail(recipientPersonOid, subject, message, Some(request))
    } catch {
      case t: Throwable =>
        //t.printStackTrace()
        BWLogger.log(getClass.getName, "saveAndSendMail()", s"ERROR ${t.getClass.getName}(${t.getMessage})")
        throw t
    }
    BWLogger.log(getClass.getName, "saveAndSendMail()", "EXIT-OK")
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val documentOid = new ObjectId(parameters("document_id"))
      val documentTimestamp = parameters("doc_version_timestamp").toLong
      val projectOid = new ObjectId(parameters("project_id"))
      val rfiText = parameters("rfi_text")
      val subject = parameters("subject")
      val recipientRoles = parameters("recipient_roles").split(",").map(_.trim)
      val millisNow = System.currentTimeMillis
      val message = Map("text" -> rfiText, "sender" -> user._id[ObjectId], "recipient_roles" -> recipientRoles,
          "read_person_ids" -> Seq.empty[String], "attachments" -> Seq.empty[String], "timestamp" -> millisNow)
      BWMongoDB3.rfi_messages.insertOne(Map("timestamps" -> Map("start" -> millisNow),
        "document" -> Map("document_id" -> documentOid, "version" -> documentTimestamp), "subject" -> subject,
        "status" -> "open", "project_id" -> projectOid, "messages" -> Seq(message)))
      //saveAndSendMail(projectOid, activityOid, theAction, isRequest, request)
      response.setContentType("application/json")
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
