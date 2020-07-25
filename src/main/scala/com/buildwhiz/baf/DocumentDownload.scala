package com.buildwhiz.baf

import java.io.InputStream
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{AmazonS3, BWMongoDB3}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

class DocumentDownload extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val documentOid = new ObjectId(parameters("document_id"))
      val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val documentHistory: Seq[DynDoc] = project.documents[Many[Document]].filter(_.document_id[ObjectId] == documentOid)
      val latestVersion: DynDoc = documentHistory.sortWith(_.timestamp[Long] < _.timestamp[Long]).last
      val amazonS3Key = f"$projectOid-$documentOid-${latestVersion.timestamp[Long]}%x"
      val inputStream: InputStream = AmazonS3.getObject(amazonS3Key)
      val outputStream = response.getOutputStream
      val buffer = new Array[Byte](4096)
      var len = inputStream.read(buffer)
      while (len > 0) {
        outputStream.write(buffer, 0, len)
        len = inputStream.read(buffer)
      }
      val document: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).head
      response.setContentType(document.content_type[String])
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
