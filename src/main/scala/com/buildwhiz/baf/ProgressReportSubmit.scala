package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse, Part}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils, MailUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.io.Source

class ProgressReportSubmit extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  private def messageBody(title: String, uri: String) =
    s"""${dateTimeString(System.currentTimeMillis)}
       |A progress report has been posted with the following title:
       |
       |&nbsp;&nbsp;&nbsp;&nbsp;<a href="$uri">$title</a>&nbsp;&nbsp;(Click link to see details)
       |
       |This email was sent as you are the project manager.""".stripMargin

  private def sendProgressMail(projectManager: ObjectId, title: String, uri: String, request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getName, s"sendProgressMail($projectManager)", "ENTRY", request)
    try {
      sendMail(Seq(projectManager), s"RFI for '$title'", messageBody(title, uri), Some(request))
    } catch {
      case t: Throwable =>
        //t.printStackTrace()
        BWLogger.log(getClass.getName, "sendMail()", s"ERROR ${t.getClass.getName}(${t.getMessage})")
        throw t
    }
    BWLogger.log(getClass.getName, "sendProgressMail()", "EXIT-OK", request)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val parts: Iterable[Part] = request.getParts.asScala
      val dataPart = parts.head
      val json = Source.fromInputStream(dataPart.getInputStream).getLines().mkString("\n")
      val data: DynDoc = Document.parse(json)
      val personOid = new ObjectId(data.person_id[String])
      val projectOid = project430ForestOid
      val timestamp = System.currentTimeMillis
      val updateType = data.update_type[String]
      val title = data.title[String]
      val docIds = mutable.Buffer.empty[ObjectId]
      val description = s"person=$personOid, timestamp=$timestamp"
      for (part <- parts.tail) {
        val name = part.getName
        val docOid = DocumentVersionUpload.createDocRecord(Some("SYSTEM"), Some("Attachment"), Some(name),
          Some(description), projectOid)
        DocumentVersionUpload.storeAmazonS3(name, part.getInputStream, projectOid.toString, docOid, timestamp, description,
          personOid, request)
        docIds.append(docOid)
      }
      BWMongoDB3.user_updates.insertOne(Map("project_id" -> projectOid, "person_id" -> personOid, "type" -> updateType,
        "title" -> title, "message" -> data.message[String], "timestamp" -> timestamp,
        "attachments" -> docIds))
      val url = request.getRequestURL.toString.split("/").reverse.drop(2).reverse.mkString("/") +
        s"/#/status-update"
      val projectRecord: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val projectManager = projectRecord.admin_person_id[ObjectId]
      sendProgressMail(projectManager, title, url, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
    BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
  }
}
