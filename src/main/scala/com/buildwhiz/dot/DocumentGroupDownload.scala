package com.buildwhiz.dot

import java.io.{InputStream, OutputStream}
import java.util.zip.{ZipEntry, ZipOutputStream}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{AmazonS3, BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId
import org.bson.Document

class DocumentGroupDownload extends HttpServlet with HttpUtils {

  private def documentNameAndStream(documentId: String, projectId: String): (String, InputStream) = {
    val documentOid = new ObjectId(documentId)
    val documentRecord: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).head
    val version: DynDoc = documentRecord.versions[Many[Document]].head
    val fileName = version.file_name[String]
    val timestamp = version.timestamp[Long]
    val amazonS3Key = f"$projectId-$documentOid-$timestamp%x"
    val inputStream: InputStream = AmazonS3.getObject(amazonS3Key).getObjectContent
    (fileName, inputStream)
  }

  private def zipMultipleDocuments(documentAndProjectIds: Seq[(String, String)], outStream: OutputStream,
        request: HttpServletRequest): Unit = {
    val zipOutputStream = new ZipOutputStream(outStream)
    for (idPair <- documentAndProjectIds) {
      val (fileName, inputStream) = documentNameAndStream(idPair._1, idPair._2)
      val zipEntry = new ZipEntry(fileName)
      zipOutputStream.putNextEntry(zipEntry)
      val bytes = Array.ofDim[Byte](1024)
      var length, totalLength = 0
      while ({length = inputStream.read(bytes); length} > 0) {
        zipOutputStream.write(bytes, 0, length)
        totalLength += length
      }
      zipOutputStream.closeEntry()
      BWLogger.log(getClass.getName, "zipMultipleDocuments",
        s"ZipOutputStream: added $totalLength bytes to '$fileName'", request)
      inputStream.close()
    }
    zipOutputStream.close()
    //outStream.close()
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    //val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val postData = getStreamData(request)
      val parameters: DynDoc = if (postData.nonEmpty) Document.parse(postData) else new Document()
      val ids: Seq[DynDoc] = if (parameters.has("ids"))
        parameters.ids[Many[Document]]
      else
        throw new IllegalArgumentException(s"parameter 'ids' not found")
      BWLogger.log(getClass.getName, "doPost",
        s"POST parameters: ${parameters.asDoc.toJson}", request)
      val docAndProjTuples = ids.map(id => (id.document_id[String], id.project_id[String]))
      val outputStream = response.getOutputStream
      zipMultipleDocuments(docAndProjTuples, outputStream, request)
      response.setContentType("application/zip")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doPost", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val documentIds = parameters("document_ids").split(",").map(_.trim)
      val projectIds = parameters("project_ids").split(",").map(_.trim)
      val docAndProjTuples = documentIds.zip(projectIds).map(id => (id._1, id._2))
      val outputStream = response.getOutputStream
      zipMultipleDocuments(docAndProjTuples, outputStream, request)
      response.setContentType("application/zip")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
