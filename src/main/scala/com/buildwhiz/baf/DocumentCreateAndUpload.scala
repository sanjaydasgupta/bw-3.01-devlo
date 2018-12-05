package com.buildwhiz.baf

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class DocumentCreateAndUpload extends HttpServlet with HttpUtils with MailUtils {

  def createProjectDocumentRecord(name: String, description: String, fileType: String, systemLabels: Seq[String],
       projectOid: ObjectId, phaseOid: Option[ObjectId] = None, action: Option[(ObjectId, String)] = None): ObjectId = {

    val query = ((phaseOid, action) match {
      case (Some(phOid), Some((actOid, actName))) => Map("phase_id" -> phOid, "activity_id" -> actOid,
        "action_name" -> actName)
      case (Some(phOid), None) => Map("phase_id" -> phOid, "activity_id" -> Map("$exists" -> false),
        "action_name" -> Map("$exists" -> false))
      case (None, Some((activityOid, actionName))) => Map("phase_id" -> Map("$exists" -> false),
        "activity_id" -> activityOid, "action_name" -> actionName)
      case (None, None) => Map("phase_id" -> Map("$exists" -> false), "activity_id" -> Map("$exists" -> false),
        "action_name" -> Map("$exists" -> false))
    }) ++ Map("project_id" -> projectOid, "name" -> name)

    if (BWMongoDB3.document_master.find(query).asScala.nonEmpty)
      throw new IllegalArgumentException(s"File named '$name' already exists")

    val assertions = query.toSeq.filterNot(_._2.isInstanceOf[Map[_, _]]).toMap
    val newDocumentRecord = new Document(Map("name" -> name, "description" -> description, "project_id" -> projectOid,
      "type" -> fileType, "timestamp" -> System.currentTimeMillis, "versions" -> Seq.empty[Document],
      "labels" -> systemLabels) ++ assertions)
    BWMongoDB3.document_master.insertOne(newDocumentRecord)
    newDocumentRecord.getObjectId("_id")
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {

      val name = parameters("name")
      val description = parameters.getOrElse("description", "???")
      val fileType = parameters.getOrElse("type", "???")
      val projectOid = new ObjectId(parameters("project_id"))
      if (BWMongoDB3.projects.find(Map("_id" -> projectOid)).isEmpty)
        throw new IllegalArgumentException(s"unknown project-id: ${projectOid.toString}")
      val labels = parameters("labels").split(",").toSeq
      val user: DynDoc = getUser(request)
      val authorOid = parameters.get("author_id") match {
        case Some(id) => new ObjectId(id)
        case None => user._id[ObjectId]
      }
      if (BWMongoDB3.persons.find(Map("_id" -> authorOid)).isEmpty)
        throw new IllegalArgumentException(s"unknown author-id: ${authorOid.toString}")
      val timestamp = parameters.get("timestamp") match {
        case Some(ts) => ts.toLong
        case None => System.currentTimeMillis
      }

      val docOid = createProjectDocumentRecord(name, description, fileType, labels, projectOid, None, None)
      BWLogger.audit(getClass.getName, request.getMethod, s"Created new document $docOid", request)

      val partCount = if (request.getContentType.contains("multipart")) request.getParts.size else 0
      if (partCount > 1)
        throw new IllegalArgumentException(s"multiple file uploads not allowed")
      if (partCount == 1) {
        val part = request.getParts.iterator.next()
        val submittedFilename = part.getSubmittedFileName
        val fullFileName = if (submittedFilename == null || submittedFilename.isEmpty)
          "unknown.tmp"
        else
          submittedFilename
        val inputStream = part.getInputStream
        val storageResult = DocumentVersionUpload.storeAmazonS3(fullFileName, inputStream, projectOid.toString,
          docOid, timestamp, "-", authorOid, request)
        val message = s"Added version (${storageResult._2} bytes) to new document '$name'"
        BWLogger.audit(getClass.getName, request.getMethod, message, request)
      } else {
        val message = s"Created new document record for '$name'"
        BWLogger.audit(getClass.getName, request.getMethod, message, request)
      }

      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

