package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class RfiList extends HttpServlet with HttpUtils with DateTimeUtils {

  private def getRfiList(request: HttpServletRequest): Seq[Document] = {
    val parameters = getParameterMap(request)
    val user: DynDoc = getUser(request)
    val userOid = user._id[ObjectId]
    val mongoQuery = Seq(
      ("document_id", "document.document_id", (id: String) => new ObjectId(id)),
      ("project_id", "project_id", (id: String) => new ObjectId(id)),
      ("doc_version_timestamp", "document.version", (ts: String) => ts.toLong)
    ).filter(t => parameters.contains(t._1)).map(t => (t._2, t._3(parameters(t._1)))).toMap
    val allRfi: Seq[DynDoc] = if (mongoQuery.nonEmpty)
      BWMongoDB3.rfi_messages.find(mongoQuery)
    else
      BWMongoDB3.rfi_messages.find(Map("members" -> userOid))
    val rfiProperties: Seq[Document] = allRfi.map(rfi => {
      val priority = if (rfi.has("priority")) rfi.priority[String] else "LOW"
      val messages: Seq[DynDoc] = rfi.messages[Many[Document]]
      val originatorOid: ObjectId = messages.head.sender[ObjectId]
      val closeable: Boolean = userOid == originatorOid
      val timestamps: DynDoc = rfi.timestamps[Document]
      val originationTime = dateTimeString(timestamps.start[Long], Some(user.tz[String]))
      val originator: DynDoc = BWMongoDB3.persons.find(Map("_id" -> originatorOid)).head
      val originatorName = s"${originator.first_name[String]} ${originator.last_name[String]}"
      new Document(Map("_id" -> rfi._id[ObjectId].toString, "priority" -> priority,
        "subject" -> rfi.subject[String], "task" -> "???", "originator" -> originatorName,
        "question" -> rfi.question[String], "state" -> rfi.status[String], "assigned_to" -> "Unknown Unknown",
        "origination_date" -> originationTime, "due_date" -> originationTime, "response_date" -> originationTime,
        "project_id" -> rfi.project_id[ObjectId].toString, "closeable" -> closeable))
    })
    rfiProperties
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    try {
      val rfiJsons = getRfiList(request).map(bson2json)
      response.getWriter.print(rfiJsons.mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK (${rfiJsons.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}