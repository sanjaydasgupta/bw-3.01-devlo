package com.buildwhiz.baf

import java.io.{File, FileOutputStream}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{AmazonS3, BWMongoDB3}
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import org.bson.Document
import org.bson.types.ObjectId

class RfiSubmit extends HttpServlet with HttpUtils with MailUtils {

  private def saveAndSendMail(projectOid: ObjectId, activityOid: ObjectId, action: DynDoc, isRequest: Boolean,
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

  private def storeDocumentAmazonS3(text: String, projectId: String, documentId: String, timestamp: Long): String = {
    BWLogger.log(getClass.getName, "storeDocumentAmazonS3", "ENTRY")
    val fileName = f"$projectId-$documentId-$timestamp%x"
    val file = new File(fileName)
    try {
      val outFile = new FileOutputStream(file)
      outFile.write(text.getBytes)
      AmazonS3.putObject(fileName, file)
      BWLogger.log(getClass.getName, "storeDocumentAmazonS3", "EXIT-OK")
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "storeDocumentAmazonS3", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        //t.printStackTrace()
        throw t
    }
    try {file.delete()} catch {case t: Throwable => /* No recovery */}
    fileName
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val documentTimestamp = System.currentTimeMillis
      val projectOid = new ObjectId(parameters("project_id"))
      val activityOid = new ObjectId(parameters("activity_id"))
      val actionName = parameters("action_name")
      val isRequest = parameters("is_request").toBoolean
      val rfiText = parameters("rfi_text")
      val documentOid = if (isRequest) rfiRequestOid else rfiResponseOid
      val theActivity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).head
      val actionNames: Seq[String] = theActivity.actions[Many[Document]].map(_.name[String])
      val actionIndex = actionNames.indexOf(actionName)
      storeDocumentAmazonS3(rfiText, projectOid.toString, documentOid.toString, documentTimestamp)
      BWMongoDB3.projects.updateOne(Map("_id" -> projectOid),
        Map("$push" -> Map("documents" -> Map("document_id" -> documentOid, "activity_id" -> activityOid,
          "action_name" -> actionName, "timestamp" -> documentTimestamp))))
      BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
        Map("$addToSet" -> Map(s"actions.$actionIndex.inbox" -> documentOid)))
      val theAction: DynDoc = theActivity.actions[Many[Document]].get(actionIndex)
      saveAndSendMail(projectOid, activityOid, theAction, isRequest, request)
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
