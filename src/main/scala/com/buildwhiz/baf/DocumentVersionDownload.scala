package com.buildwhiz.baf

import java.io.InputStream
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.HttpUtils
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{AmazonS3, BWLogger, BWMongoDB3}
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class DocumentVersionDownload extends HttpServlet with HttpUtils {

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
      val amazonS3Key = f"$project430ForestOid-$documentOid-$timestamp%x"
      val inputStream: InputStream = AmazonS3.getObject(amazonS3Key).getObjectContent
      val outputStream = response.getOutputStream
      val buffer = new Array[Byte](4096)
      var len = inputStream.read(buffer)
      while (len > 0) {
        outputStream.write(buffer, 0, len)
        len = inputStream.read(buffer)
      }
      val document: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).asScala.head
      response.setContentType(contentTypes(document.content[String]))
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
