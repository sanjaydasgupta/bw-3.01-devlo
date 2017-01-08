package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import com.buildwhiz.{DateTimeUtils, HttpUtils, MailUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class RFIMessagesFetch extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val rfiExchanges: Seq[DynDoc] = BWMongoDB3.rfi_messages.find(Map("project_id" -> project430ForestOid,
        "members" -> personOid)).asScala.toSeq
      val rfiLines: Seq[Document] = rfiExchanges.map(exchange => {
        val messages: Seq[DynDoc] = exchange.messages[DocumentList]
        val lastMessage = messages.last
        val sender: DynDoc = BWMongoDB3.persons.find(Map("_id" -> lastMessage.sender[ObjectId])).asScala.head
        val senderName = s"${sender.first_name[String]} ${sender.last_name[String]}"
        val clientTimezone = parameters("tz")
        new Document(Map("_id" -> exchange._id[ObjectId], "subject" -> exchange.subject[String],
          "timestamp" -> dateTimeString(lastMessage.timestamp[Long], Some(clientTimezone)),
          "last_message" -> lastMessage.text[String], "sender" -> senderName))
      })
      response.getWriter.print(rfiLines.map(bson2json).mkString("[", ", ", "]"))
      response.setContentType("application/json")
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
