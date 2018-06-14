package com.buildwhiz.dot

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class GetRfisSummary extends HttpServlet with HttpUtils with DateTimeUtils {

  private def getRfis(request: HttpServletRequest): Seq[Document] = {
    val parameters = getParameterMap(request)
    val user: DynDoc = getUser(request)
    val documentOid = parameters.get("document_id").map(id => new ObjectId(id))
    val rfis: Seq[DynDoc] = documentOid match {
      case None => BWMongoDB3.rfi_messages.find()
      case Some(oid) => BWMongoDB3.rfi_messages.find(Map("document_id" -> oid))
    }
    val rfiProperties: Seq[Document] = rfis.map(rfi => {
      val messages: Seq[DynDoc] = rfi.messages[Many[Document]]
      val rfiProps: Document = {
        val lastMessage: DynDoc = messages.sortWith(_.timestamp[Long] < _.timestamp[Long]).last
        val date = dateTimeString(lastMessage.timestamp[Long], Some(user.tz[String]))
        val authorOid = lastMessage.sender[ObjectId]
        val author: DynDoc = BWMongoDB3.persons.find(Map("_id" -> authorOid)).head
        val authorName = s"${author.first_name[String]} ${author.last_name[String]}"
        Map("subject" -> rfi.subject[String], "text" -> lastMessage.text[String], "documents" -> "???",
          "author" -> authorName, "date" -> date)
      }
      rfiProps
    })
    rfiProperties
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val allRfis = getRfis(request)
      writer.print(allRfis.map(document => bson2json(document)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK (${allRfis.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}