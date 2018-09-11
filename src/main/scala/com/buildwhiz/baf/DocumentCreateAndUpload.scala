package com.buildwhiz.baf

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
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

    val assertions = query.toSeq.filterNot(_._2.isInstanceOf[Map[String, _]]).toMap
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

      if (request.getParts.size != 1)
        throw new IllegalArgumentException(s"parts.length != 1")
      val part = request.getParts.iterator.next()
      val uploadSize = part.getSize
      if (uploadSize > 1e7)
        throw new IllegalArgumentException(s"attachment size > 10Mb")
      val submittedFilename = part.getSubmittedFileName
      val fullFileName = if (submittedFilename == null || submittedFilename.isEmpty)
        "unknown.tmp"
      else
        submittedFilename
      val inputStream = part.getInputStream
      val name = parameters("name")
      val description = parameters("description")
      val fileType = parameters("type")
      val projectOid = new ObjectId(parameters("project_id"))
      val labels = parameters("labels").split(",").toSeq
      val user: DynDoc = getUser(request)
      val timestamp = System.currentTimeMillis

      val docOid = createProjectDocumentRecord(name, description, fileType, labels, projectOid, None, None)
      BWLogger.audit(getClass.getName, request.getMethod, s"Created new document $docOid", request)
      val storageResult = DocumentVersionUpload.storeAmazonS3(fullFileName, inputStream, projectOid.toString,
        docOid, timestamp, "-", user._id[ObjectId], request)

      response.setStatus(HttpServletResponse.SC_OK)
      val message = s"Added version (${storageResult._2} bytes) to new document '$name'"
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

