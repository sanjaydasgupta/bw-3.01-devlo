package com.buildwhiz.baf

import java.io.InputStream

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.buildwhiz.infra.{/*AmazonS3, */BWMongoDB3, DynDoc, GoogleDrive}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

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
      val documentRecord: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).head
      val projectOid = if (documentRecord.has("project_id"))
        documentRecord.project_id[ObjectId]
      else
        project430ForestOid
      val amazonS3Key = f"$projectOid-$documentOid-$timestamp%x"
      BWLogger.log(getClass.getName, "doGet", s"amazonS3Key: $amazonS3Key", request)
      //val inputStream: InputStream = AmazonS3.getObject(amazonS3Key)
      val inputStream: InputStream = GoogleDrive.getObject(amazonS3Key)
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
