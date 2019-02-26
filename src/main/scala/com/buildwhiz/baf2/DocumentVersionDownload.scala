package com.buildwhiz.baf2

import java.io.InputStream

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{AmazonS3, BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class DocumentVersionDownload extends HttpServlet with HttpUtils {

  private val contentTypes = Map("MS-Excel" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "MS-PPT" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "MS-Word" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "PDF" -> "application/pdf", "Text" -> "text/plain", "XML" -> "application/xml")

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val timestamp = parameters("timestamp").toLong
      val documentOid = new ObjectId(parameters("document_master_id"))
      val documentRecord: DynDoc = DocumentApi.documentById(documentOid)
      val projectOid = documentRecord.project_id[ObjectId]
      if (!ProjectApi.exists(projectOid))
        throw new IllegalArgumentException(s"Bad project-id: '$projectOid'")
      val amazonS3Key = f"$projectOid-$documentOid-$timestamp%x"
      BWLogger.log(getClass.getName, request.getMethod, s"amazonS3Key: $amazonS3Key", request)
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
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
