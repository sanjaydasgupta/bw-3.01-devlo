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
    val mongoQuery = Seq(
      ("document_id", "document.document_id", (id: String) => new ObjectId(id)),
      ("project_id", "project_id", (id: String) => new ObjectId(id)),
      ("doc_version_timestamp", "document.version", (ts: String) => ts.toLong)
    ).filter(t => parameters.contains(t._1)).map(t => (t._2, t._3(parameters(t._1)))).toMap
    val allRfi: Seq[DynDoc] = if (mongoQuery.nonEmpty)
      BWMongoDB3.rfi_messages.find(mongoQuery)
    else
      BWMongoDB3.rfi_messages.find()
    val rfiProperties: Seq[Document] = allRfi.map(rfi => {
      val messages: Seq[DynDoc] = rfi.messages[Many[Document]]
      val ownerOid: ObjectId = messages.head.sender[ObjectId]
      val closeable: Boolean = user._id[ObjectId] == ownerOid
      val rfiProps: Document = {
        val lastMessage: DynDoc = messages.sortWith(_.timestamp[Long] < _.timestamp[Long]).last
        val date = dateTimeString(lastMessage.timestamp[Long], Some(user.tz[String]))
        val authorOid = lastMessage.sender[ObjectId]
        val author: DynDoc = BWMongoDB3.persons.find(Map("_id" -> authorOid)).head
        val authorName = s"${author.first_name[String]} ${author.last_name[String]}"
        Map("subject" -> rfi.subject[String], "text" -> lastMessage.text[String], "documents" -> "???",
          "author" -> authorName, "date" -> date, "closeable" -> closeable)
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