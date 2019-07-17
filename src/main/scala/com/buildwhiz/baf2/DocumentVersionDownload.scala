package com.buildwhiz.baf2

import java.io.InputStream

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{AmazonS3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class DocumentVersionDownload extends HttpServlet with HttpUtils {

  private val contentTypes = Map(
    "bmp" -> "image/bmp",
    "doc" -> "application/msword",
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "gif" -> "image/gif",
    "jpg" -> "image/jpeg",
    "jpeg" -> "image/jpeg",
    "pdf" -> "application/pdf",
    "ppt" -> "application/vnd.ms-powerpoint",
    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "svg" -> "image/svg+xml",
    "tif" -> "image/tiff",
    "tiff" -> "image/tiff",
    "txt" -> "text/plain",
    "xls" -> "application/vnd.ms-excel",
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "xml" -> "application/xml",
    "zip" -> "application/zip"
  )

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
      val version = documentRecord.versions[Many[DynDoc]].find(_.timestamp == timestamp)
      version match {
        case None =>
        case Some(v) => if (v.has("file_name")) {
          val fileType = v.file_name[String].split("\\.").last.toLowerCase
          if (contentTypes.contains(fileType)) {
            val contentType = contentTypes(fileType)
            response.setContentType(contentType)
            BWLogger.log(getClass.getName, request.getMethod, s"Content-Type set: $contentType", request)
          } else {
            BWLogger.log(getClass.getName, request.getMethod, s"No Content-Type for: $fileType", request)
          }
        }
      }
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
