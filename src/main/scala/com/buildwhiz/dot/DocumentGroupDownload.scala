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

  private def zipMultipleStreams(inputs: Seq[(String, InputStream)], outStream: OutputStream) = {
    val zipOutputStream = new ZipOutputStream(outStream)
    for ((fileName, inputStream) <- inputs) {
      val zipEntry = new ZipEntry(fileName)
      zipOutputStream.putNextEntry(zipEntry)
      val bytes = Array.ofDim[Byte](1024)
      var length = 0
      while ({length = inputStream.read(bytes); length} >= 0) {
        zipOutputStream.write(bytes, 0, length)
      }
      inputStream.close()
    }
    zipOutputStream.close()
    //outStream.close()
  }

  val contentTypes = Map("MS-Excel" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "MS-PPT" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "MS-Word" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "PDF" -> "application/pdf", "Text" -> "text/plain", "XML" -> "application/xml")

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val timestamp = parameters("timestamp").toLong
      val documentOid = new ObjectId(parameters("document_master_id"))
      val docRec: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).head
      val version: DynDoc = docRec.versions[Many[Document]].find(_.timestamp[Long] == timestamp).get
      val fileName = version.file_name[String]
      val amazonS3Key = f"$project430ForestOid-$documentOid-$timestamp%x"
      val inputStream: InputStream = AmazonS3.getObject(amazonS3Key).getObjectContent
      val outputStream = response.getOutputStream
      val buffer = new Array[Byte](4096)
      var len = inputStream.read(buffer)
      while (len > 0) {
        outputStream.write(buffer, 0, len)
        len = inputStream.read(buffer)
      }
      val document: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).head
      if (document.has("content") && contentTypes.contains(document.content[String]))
        response.setContentType(contentTypes(document.content[String]))
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
