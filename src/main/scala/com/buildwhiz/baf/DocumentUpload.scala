package com.buildwhiz.baf

import java.io.{File, FileOutputStream, InputStream}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{AmazonS3, BWLogger, BWMongoDB3, Utils}
import BWMongoDB3._
import org.bson.types.ObjectId

import scala.annotation.tailrec
import scala.collection.JavaConversions._

class DocumentUpload extends HttpServlet with Utils {

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
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "storeDocumentAmazonS3", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})")
        t.printStackTrace()
        throw t
    }
    try {file.delete()} catch {case t: Throwable => /* No recovery */}
    (fileName, fileLength)
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
      val activityOid = new ObjectId(parameters("activity_id"))
      // Add document to project's "documents" list
      val projectsUpdateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid),
        Map("$push" -> Map("documents" -> Map("document_id" -> documentOid, "activity_id" -> activityOid,
          "action_name" -> parameters("action_name"), "timestamp" -> documentTimestamp))))
      // Add document to action's inbox
      val theActivity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).head
      val actionNames: Seq[String] = theActivity.actions[DocumentList].map(_.name[String])
      val actionIndex = actionNames.indexOf(parameters("action_name"))
      if (projectsUpdateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $projectsUpdateResult")
      BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
        Map("$addToSet" -> Map(s"actions.$actionIndex.inbox" -> documentOid)))
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
