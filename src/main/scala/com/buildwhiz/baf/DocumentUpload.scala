package com.buildwhiz.baf

import java.io.{File, FileOutputStream, InputStream}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{AmazonS3, BWMongoDB3}
import BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.annotation.tailrec
class DocumentUpload extends HttpServlet with HttpUtils with MailUtils {


  private def storeDocumentAmazonS3(is: InputStream, projectId: String, documentId: String, timestamp: Long):
      (String, Long) = {
    BWLogger.log(getClass.getName, "storeDocumentAmazonS3", "ENTRY")
    val fileName = f"$projectId-$documentId-$timestamp%x"
    val file = new File(fileName)
    var fileLength = 0L
    try {
      val outFile = new FileOutputStream(file)
      val buffer = new Array[Byte](1024)
      @tailrec def handleBlock(length: Int = 0): Int = {
        val bytesRead = is.read(buffer)
        if (bytesRead > 0) {
          outFile.write(buffer, 0, bytesRead)
          handleBlock(length + bytesRead)
        } else {
          outFile.close()
          length
        }
      }
      fileLength = handleBlock()
      AmazonS3.putObject(fileName, file)
      BWLogger.log(getClass.getName, "storeDocumentAmazonS3", "EXIT-OK")
      try {file.delete()} catch {case t: Throwable => /* No recovery */}
      (fileName, fileLength)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "storeDocumentAmazonS3", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        //t.printStackTrace()
        throw t
    }
  }

  private def saveAndSendMail(projectOid: ObjectId, activityOid: ObjectId, action: DynDoc, documentOid: ObjectId,
        request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getName, "saveAndSendMail()", "ENTRY")
    try {
      val reqOrResp = if (documentOid == rfiRequestOid) "request" else "response"
      val subject = s"RFI $reqOrResp received"
      val message = s"You have a RFI $reqOrResp for action '${action.name[String]}'"
      val recipientPersonOid: ObjectId = if (documentOid == rfiRequestOid) {
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
      val documentTimestamp = System.currentTimeMillis
      val result = storeDocumentAmazonS3(request.getInputStream, parameters("project_id"),
        parameters("document_id"), documentTimestamp)
      val projectOid = new ObjectId(parameters("project_id"))
      val documentOid = new ObjectId(parameters("document_id"))
      val activityOid = parameters.get("activity_id").map(new ObjectId(_))
      val actionName = parameters.get("action_name")
      // Add document to project's "documents" list
      val projectsUpdateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid),
        Map("$push" -> Map("documents" -> Seq("document_id" -> documentOid, "activity_id" -> activityOid.orNull,
          "action_name" -> actionName.orNull, "timestamp" -> documentTimestamp).filter(_._2 != null).toMap)))
      // Add document to action's inbox
      if (activityOid.isDefined && actionName.isDefined) {
        val theActivity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid.get)).head
        val actionNames: Seq[String] = theActivity.actions[Many[Document]].map(_.name[String])
        val actionIndex = actionNames.indexOf(actionName.get)
        BWMongoDB3.activities.updateOne(Map("_id" -> activityOid.get),
          Map("$addToSet" -> Map(s"actions.$actionIndex.inbox" -> documentOid)))
        if (documentOid == rfiRequestOid || documentOid == rfiResponseOid) {
          saveAndSendMail(projectOid, activityOid.get, theActivity.actions[Many[Document]].get(actionIndex),
            documentOid, request: HttpServletRequest)
        }
      }
      if (projectsUpdateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $projectsUpdateResult")
      response.getWriter.print(s"""{"fileName": "${result._1}", "length": ${result._2}}""")
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doPost", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
