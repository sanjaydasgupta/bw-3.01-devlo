package com.buildwhiz.baf

import java.io.{File, FileOutputStream, InputStream}
import javax.servlet.annotation.MultipartConfig
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse, Part}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{AmazonS3, BWLogger, BWMongoDB3}
import com.buildwhiz.{HttpUtils, MailUtils}
import org.bson.types.ObjectId

import scala.annotation.tailrec
import scala.collection.JavaConverters._

//@MultipartConfig() Not needed -- already exists upstream (Entry)
class ProgressReportSubmit extends HttpServlet with HttpUtils with MailUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doPost", s"""ENTRY""", request)
    val parameters = getParameterMap(request)
    val parts: Seq[Part] = request.getParts.asScala.toSeq
    BWLogger.log(getClass.getName, "doPost:request-info", s"Content-Long-Length: ${request.getContentLengthLong}, " +
     s"Content-Type: ${request.getContentType} Parts-Length: ${parts.length}", request)
    if (parts.nonEmpty) {
      val partDescriptions = parts.map(p => s"""[name: ${p.getName}, type: ${p.getContentType}, size: ${p.getSize}]""")
      BWLogger.log(getClass.getName, "doPost:parts", partDescriptions.mkString(", "), request)
    }
    BWLogger.log(getClass.getName, "doPost", "EXIT-OK", request)
  }
}
