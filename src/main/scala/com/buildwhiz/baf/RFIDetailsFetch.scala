package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import com.buildwhiz.{DateTimeUtils, HttpUtils, MailUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class RFIDetailsFetch extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val rfiOid = new ObjectId(parameters("rfi_id"))
      val rfiExchange: DynDoc = BWMongoDB3.rfi_messages.find(Map("_id" -> rfiOid)).asScala.head
      val messages: Seq[DynDoc] = rfiExchange.messages[DocumentList]
      val messageLines: Seq[Document] = messages.map(message => {
        val sender: DynDoc = BWMongoDB3.persons.find(Map("_id" -> message.sender[ObjectId])).asScala.head
        val senderName = s"${sender.first_name[String]} ${sender.last_name[String]}"
        val clientTimezone = parameters("tz")
        new Document(Map("timestamp" -> dateTimeString(message.timestamp[Long], Some(clientTimezone)),
          "text" -> message.text[String], "sender" -> senderName))
      })
      response.getWriter.print(messageLines.map(bson2json).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${messageLines.length} objects)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
