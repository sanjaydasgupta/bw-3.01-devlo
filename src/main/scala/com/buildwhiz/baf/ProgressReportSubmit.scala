package com.buildwhiz.baf

import java.io.{File, FileOutputStream, InputStream}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{AmazonS3, BWLogger, BWMongoDB3}
import com.buildwhiz.{HttpUtils, MailUtils}
import org.bson.types.ObjectId

import scala.annotation.tailrec
import scala.collection.JavaConverters._

class ProgressReportSubmit extends HttpServlet with HttpUtils with MailUtils {

  private val rfiRequestOid = new ObjectId("56fe4e6bd5d8ad3da60d5d38")
  private val rfiResponseOid = new ObjectId("56fe4e6bd5d8ad3da60d5d39")

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
  }
}
