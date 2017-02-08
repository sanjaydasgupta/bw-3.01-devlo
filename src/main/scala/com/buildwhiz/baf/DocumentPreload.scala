package com.buildwhiz.baf

import java.io.{File, FileOutputStream, InputStream}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{AmazonS3, BWMongoDB3}
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.annotation.tailrec

class DocumentPreload extends HttpServlet with HttpUtils with MailUtils {

  import DocumentPreload._

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val parts = request.getParts.asScala.toList
      if (parts.length > 1 && parameters.contains("document_master_id"))
        throw new IllegalArgumentException(s"Provided parts.length > 1 and 'document_master_id'")
      val storageResults = mutable.Buffer.empty[Document]
      for (part <- parts) {
        val fileName = part.getSubmittedFileName
        if (parts.length > 1)
          parameters.put("name", fileName.split("\\.").init.mkString("."))
        val timestamp = parameters("timestamp").toLong
        val documentOid = if (parameters.contains("document_master_id")) {
          val docOid = new ObjectId(parameters("document_master_id"))
          val tsExists = BWMongoDB3.document_master.find(Map("$and" -> Seq(Map("_id" -> docOid),
            Map("versions" -> Map("$elemMatch" -> Map("timestamp" -> timestamp)))))).asScala.nonEmpty
          if (tsExists)
            throw new IllegalArgumentException("An Entry for this timestamp already exists")
          docOid
        } else {
          createDocRecord(parameters.get("category"), parameters.get("subcategory"), parameters.get("name"),
            parameters.get("description"), project430ForestOid)
        }
        val inputStream = part.getInputStream
        val result = storeAmazonS3(inputStream, project430ForestOid.toString, documentOid, timestamp)
        val comments = if (parameters.contains("comments")) parameters("comments") else "-"
        val authorOid = new ObjectId(parameters("author_person_id"))
        val versionRecord = Map("comments" -> comments, "timestamp" -> timestamp, "author_person_id" -> authorOid,
          "file_name" -> fileName)
        val updateResult = BWMongoDB3.document_master.updateOne(Map("_id" -> documentOid),
          Map("$push" -> Map("versions" -> versionRecord)))
        storageResults.append(Map("document_id" -> documentOid, "timestamp" -> timestamp, "file_name" -> fileName))
        if (updateResult.getModifiedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      }
      response.getWriter.print(storageResults.map(bson2json).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doPost", s"EXIT-OK (${storageResults.length} items)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
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

  def storeAmazonS3(is: InputStream, projectId: String, documentOid: ObjectId, timestamp: Long): (String, Long) = {
    BWLogger.log(getClass.getName, "storeAmazonS3", "ENTRY")
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
      BWLogger.log(getClass.getName, s"storeAmazonS3 ($fileLength)", "EXIT-OK")
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "storeAmazonS3", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        t.printStackTrace()
        throw t
    }
    try {file.delete()} catch {case _: Throwable => /* No recovery */}
    (fileName, fileLength)
  }

}
