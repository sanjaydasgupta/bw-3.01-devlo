package com.buildwhiz.baf

import java.io.{File, FileOutputStream, InputStream}

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
//import com.buildwhiz.infra.{AmazonS3, BWMongoDB3}
import com.buildwhiz.infra.{BWMongoDB3, GoogleDrive}
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.annotation.tailrec

class DocumentPreload extends HttpServlet with HttpUtils with MailUtils {

  import DocumentPreload._

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val parts = request.getParts.asScala.toList
      if (parts.length > 1 && parameters.contains("document_master_id"))
        throw new IllegalArgumentException(s"Provided parts.length > 1 and 'document_master_id'")
      val storageResults = mutable.Buffer.empty[Document]
      for (part <- parts) {
        val projectOid = if (parameters.contains("project_id"))
          new ObjectId(parameters("project_id"))
        else
          project430ForestOid
        val fileName = part.getSubmittedFileName
        if (parts.length > 1)
          parameters.put("name", fileName.split("\\.").init.mkString("."))
        val timestamp = if (parameters.contains("timestamp"))
          parameters("timestamp").toLong
        else
          System.currentTimeMillis
        val documentOid = if (parameters.contains("document_master_id")) {
          val docOid = new ObjectId(parameters("document_master_id"))
          val tsExists = BWMongoDB3.document_master.find(Map("$and" -> Seq(Map("_id" -> docOid),
            Map("versions" -> Map("$elemMatch" -> Map("timestamp" -> timestamp)))))).asScala.nonEmpty
          if (tsExists)
            throw new IllegalArgumentException("An Entry for this timestamp already exists")
          docOid
        } else {
          createDocRecord(parameters.get("category"), parameters.get("subcategory"), parameters.get("name"),
            parameters.get("description"), projectOid)
        }
        val inputStream = part.getInputStream
        val comments = if (parameters.contains("comments")) parameters("comments") else "-"
        val authorOid = if (parameters.contains("author_person_id"))
          new ObjectId(parameters("author_person_id"))
        else
          getUser(request).get("_id").asInstanceOf[ObjectId]
        storeAmazonS3(fileName, inputStream, projectOid.toString, documentOid, timestamp, comments,
          authorOid, request)
        storageResults.append(Map("document_id" -> documentOid, "timestamp" -> timestamp, "file_name" -> fileName))
      }
      response.getWriter.print(storageResults.map(bson2json).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${storageResults.length} items)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object DocumentPreload {

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
      //AmazonS3.putObject(s3key, file)
      GoogleDrive.putObject(s3key, file)
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
