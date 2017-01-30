package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import com.buildwhiz.utils.{DateTimeUtils, HttpUtils, MailUtils}
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class RFIFetchStatus extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val rfiRecords: Seq[DynDoc] = BWMongoDB3.rfi_messages.find(Map("members" -> personOid)).asScala.toSeq
      val messages: Seq[DynDoc] = rfiRecords.flatMap(_.messages[DocumentList])
      val newMessagesCount = messages.count(msg => !msg.read_person_ids[ObjectIdList].contains(personOid))
      val status = s"""{"total": ${messages.length}, "unread": $newMessagesCount}"""
      response.getWriter.print(status)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK ($status)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
