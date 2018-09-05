package com.buildwhiz.baf

import java.io.{File, FileOutputStream, InputStream}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{AmazonS3, BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.annotation.tailrec
import scala.collection.JavaConverters._

// {
//    "_id":{"$oid":"5b84f6d957fbc004530e56ba"},
//    "name":"Completion Info for ?And Another One?",
//    "mandatory":true,"action_name":"And Another One",
//    "description":"Description of Completion Info for ?And Another One?",
//    "versions":[{"comments":"-","timestamp":{"$numberLong":"1535625698704"},
//      "author_person_id":{"$oid":"5acf514e4ac8efe4e19e91b4"},"file_name":null}],
//    "content_type":"excel","labels":["billing","design"],"activity_id":{"$oid":"5b84f61257fbc004530e565c"},
//    "project_id":{"$oid":"5b7fc55957fbc00450fa5b07"}
// }

class DocumentVersionUpload extends HttpServlet with HttpUtils with MailUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val documentOid = new ObjectId(parameters("document_id"))
      val comments = if (parameters.contains("comments")) parameters("comments") else "-"

      val user: DynDoc = getUser(request)
      val authorOid = user._id[ObjectId]

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

      val timestamp = System.currentTimeMillis

      val documentRecord: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).head
      val projectOid = documentRecord.project_id[ObjectId]

      val inputStream = part.getInputStream

      val storageResults = DocumentVersionUpload.storeAmazonS3(fullFileName, inputStream, projectOid.toString,
        documentOid, timestamp, comments, authorOid, request)

      //response.getWriter.print(storageResults)
      //response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      val message = s"Added version (${storageResults._2} bytes) to file ${documentRecord.name[String]}"
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object DocumentVersionUpload {

  def createProjectDocumentRecord(name: String, description: String, systemLabels: Seq[String],
        projectOid: ObjectId): ObjectId = {
    if (BWMongoDB3.document_master.find(Map("project_id" -> projectOid, "name" -> name,
        "phase_id" -> Map("$exists" -> false), "activity_id" -> Map("$exists" -> false))).asScala.nonEmpty)
      throw new IllegalArgumentException(s"File named '$name' already exists")
    val newDocument = new Document(Map("name" -> name, "description" -> description, "project_id" -> projectOid,
      "timestamp" -> System.currentTimeMillis, "versions" -> Seq.empty[Document], "labels" -> systemLabels))
    BWMongoDB3.document_master.insertOne(newDocument)
    newDocument.getObjectId("_id")
  }

  def createDocRecord(category: Option[String], subcategory: Option[String], name: Option[String],
      description: Option[String], projectOid: ObjectId): ObjectId = {
    val properties = Seq("category", "subcategory", "name", "description").
      zip(Seq(category, subcategory, name, description)).filter(_._2.isDefined).map(t => (t._1, t._2.get))
    val query = (("project_id" -> projectOid) +: properties.filter(_._2 != "Any")).toMap
    if (BWMongoDB3.document_master.find(query).asScala.nonEmpty)
      throw new Throwable("Record already exists")
    val newDocument = new Document(query ++ Map("timestamp" -> System.currentTimeMillis,
      "versions" -> Seq.empty[Document]))
    BWMongoDB3.document_master.insertOne(newDocument)
    newDocument.getObjectId("_id")
  }

  def storeAmazonS3(fileName: String, is: InputStream, projectId: String, documentOid: ObjectId, timestamp: Long,
      comments: String, authorOid: ObjectId, request: HttpServletRequest): (String, Long) = {
    BWLogger.log(getClass.getName, "storeAmazonS3", "ENTRY", request)
    val s3key = f"$projectId-$documentOid-$timestamp%x"
    BWLogger.log(getClass.getName, "storeAmazonS3", s"amazonS3Key: $s3key", request)
    val file = new File(s3key)
    var fileLength = 0L
    try {
      val outFile = new FileOutputStream(file)
      val buffer = new Array[Byte](4096)
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
      AmazonS3.putObject(s3key, file)
      val versionRecord = Map("comments" -> comments, "timestamp" -> timestamp, "author_person_id" -> authorOid,
        "file_name" -> fileName)
      val updateResult = BWMongoDB3.document_master.
        updateOne(Map("_id" -> documentOid), Map("$push" -> Map("versions" -> versionRecord)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      BWLogger.log(getClass.getName, s"storeAmazonS3 ($fileLength)", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "storeAmazonS3", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
    try {file.delete()} catch {case _: Throwable => /* No recovery */}
    (s3key, fileLength)
  }

}
