package com.buildwhiz.baf2

import java.io.InputStream

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{AmazonS3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId
import org.bson.Document

class DocumentVersionDownload extends HttpServlet with HttpUtils {

  private val contentDescriptions: Map[String, (String, Boolean)] = Map(
    // file-extension -> (content-type, content-disposition-inline)
    "bmp" -> ("image/bmp", true),
    "doc" -> ("application/msword", false),
    "docx" -> ("application/vnd.openxmlformats-officedocument.wordprocessingml.document", false),
    "gif" -> ("image/gif", true),
    "jpg" -> ("image/jpeg", true),
    "jpeg" -> ("image/jpeg", true),
    "pdf" -> ("application/pdf", true),
    "ppt" -> ("application/vnd.ms-powerpoint", true),
    "pptx" -> ("application/vnd.openxmlformats-officedocument.presentationml.presentation", true),
    "svg" -> ("image/svg+xml", true),
    "tif" -> ("image/tiff", true),
    "tiff" -> ("image/tiff", true),
    "txt" -> ("text/plain", true),
    "xls" -> ("application/vnd.ms-excel", true),
    "xlsx" -> ("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", true),
    "xml" -> ("application/xml", false),
    "zip" -> ("application/zip", false)
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
      val version: Option[DynDoc] = documentRecord.versions[Many[Document]].find(_.timestamp == timestamp)
      version match {
        case None =>
        case Some(v) => if (v.has("file_name")) {
          val fileName = v.file_name[String]
          val fileType = fileName.split("\\.").last.toLowerCase
          if (contentDescriptions.contains(fileType)) {
            val contentDescription = contentDescriptions(fileType)
            response.setContentType(contentDescription._1)
            val contentDisposition = if (contentDescription._2) "inline" else s"""attachment; filename="$fileName""""
            response.setHeader("Content-Disposition", contentDisposition)
            val message = s"Set Content-Type='${contentDescription._1}', Content-Disposition='$contentDisposition'"
            BWLogger.log(getClass.getName, request.getMethod, message, request)
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
