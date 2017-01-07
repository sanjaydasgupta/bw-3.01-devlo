package com.buildwhiz.baf

import java.io.{File, FileOutputStream, InputStream}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{AmazonS3, BWLogger, BWMongoDB3}
import com.buildwhiz.{HttpUtils, MailUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._
import scala.collection.mutable

import scala.annotation.tailrec

class DocumentPreload extends HttpServlet with HttpUtils with MailUtils {

  private def createRecord(parameters: mutable.Map[String, String], contentType: String): ObjectId = {
    val properties = Seq("category", "subcategory", "name", "description")
    val query = (("project_id" -> project430ForestOid) +: ("content" -> contentType) +:
      properties.map(p => (p, parameters(p))).filter(kv => kv._2.nonEmpty && kv._2 != "Any")).toMap
    if (BWMongoDB3.document_master.find(query).asScala.nonEmpty)
      throw new Throwable("Record already exists")
    val newDocument = new Document(query ++ Map("timestamp" -> System.currentTimeMillis,
      "versions" -> Seq.empty[Document]))
    BWMongoDB3.document_master.insertOne(newDocument)
    newDocument.getObjectId("_id")
  }

  private def storeDocumentAmazonS3(is: InputStream, projectId: String, documentOid: ObjectId, timestamp: Long):
      (String, Long) = {
    BWLogger.log(getClass.getName, "storeDocumentAmazonS3", "ENTRY")
    val fileName = f"$projectId-$documentOid-$timestamp%x"
    val file = new File(fileName)
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
      AmazonS3.putObject(fileName, file)
      BWLogger.log(getClass.getName, s"storeDocumentAmazonS3 ($fileLength)", "EXIT-OK")
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "storeDocumentAmazonS3", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        t.printStackTrace()
        throw t
    }
    try {file.delete()} catch {case _: Throwable => /* No recovery */}
    (fileName, fileLength)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val timestamp = parameters("timestamp").toLong
      val part = request.getParts.iterator().next()
      val inputStream = part.getInputStream
      val fileName = part.getSubmittedFileName
      val fileExtension = fileName.split("\\.").last.toUpperCase
      val documentOid = if (parameters.contains("document_master_id"))
          new ObjectId(parameters("document_master_id")) else createRecord(parameters, fileExtension)
      val result = storeDocumentAmazonS3(inputStream, project430ForestOid.toString, documentOid, timestamp)
      val comments = parameters("comments")
      val authorOid = new ObjectId(parameters("author_person_id"))
      val versionRecord = Map("comments" -> comments, "timestamp" -> timestamp, "author_person_id" -> authorOid,
        "file_name" -> fileName)
      val updateResult = BWMongoDB3.document_master.updateOne(Map("_id" -> documentOid),
          Map("$push" -> Map("versions" -> versionRecord)))
      if (updateResult.getModifiedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      response.getWriter.print(s"""{"fileName": "${result._1}", "length": ${result._2}}""")
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doPost", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
