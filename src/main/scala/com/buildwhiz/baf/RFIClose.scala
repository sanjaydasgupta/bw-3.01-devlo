package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import BWMongoDB3._
import com.buildwhiz.{HttpUtils, MailUtils}
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class RFIClose extends HttpServlet with HttpUtils with MailUtils {

  private def messageBody(subject: String) =
    s"""An RFI message with the following subject has been closed:
      |
      |    '$subject'
      |
      |This email was sent as you are either a manager or an author.""".stripMargin

  private def sendMail(members: Seq[ObjectId], subject: String): Unit = {
    BWLogger.log(getClass.getName, s"sendMail($members)", "ENTRY")
    try {
      sendMail(members, s"RFI closed '$subject'", messageBody(subject))
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "sendMail()", s"ERROR ${t.getClass.getName}(${t.getMessage})")
    }
    BWLogger.log(getClass.getName, "sendMail()", "EXIT-OK")
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
      val members: Seq[ObjectId] = rfiRecord.members[ObjectIdList].asScala
      val originatorOid = getUser(request).get("_id").asInstanceOf[ObjectId]
      sendMail(members.filterNot(_ == originatorOid), rfiRecord.subject[String])
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
