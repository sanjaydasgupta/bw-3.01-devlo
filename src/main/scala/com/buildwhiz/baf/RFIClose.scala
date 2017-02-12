package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils, MailUtils}
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class RFIClose extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  private def messageBody(subject: String, uri: String) =
    s"""${dateTimeString(System.currentTimeMillis)}
      |An RFI with the following subject has been closed:
      |
      |&nbsp;&nbsp;&nbsp;&nbsp;<a href="$uri">$subject</a>&nbsp;&nbsp;(Click link to see details)
      |
      |This email was sent as you are either a manager or an author.""".stripMargin

  private def sendRFIMail(members: Seq[ObjectId], subject: String, uri: String): Unit = {
    BWLogger.log(getClass.getName, s"sendRFIMail($members)", "ENTRY")
    sendMail(members, s"RFI closed '$subject'", messageBody(subject, uri))
    BWLogger.log(getClass.getName, "sendRFIMail()", "EXIT-OK")
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val rfiOid = new ObjectId(parameters("rfi_id"))
      val updateResult = BWMongoDB3.rfi_messages.updateOne(Map("_id" -> rfiOid),
          Map("$set" -> Map("status" -> "closed", "timestamps.close" -> System.currentTimeMillis)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      val rfiRecord: DynDoc = BWMongoDB3.rfi_messages.find(Map("_id" -> rfiOid)).asScala.head
      val members: Seq[ObjectId] = rfiRecord.members[Many[ObjectId]].asScala
      val originatorOid = getUser(request).get("_id").asInstanceOf[ObjectId]
      val url = request.getRequestURL.toString.split("/").reverse.drop(2).reverse.mkString("/") +
        s"/#/rfi?rfi_id=$rfiOid"
      sendRFIMail(members.filterNot(_ == originatorOid), rfiRecord.subject[String], url)
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
