package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class ProgressReportList extends HttpServlet with HttpUtils with DateTimeUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val timezone = parameters("timezone")
      val user: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
      val isAdmin = user.roles[Many[String]].contains("BW-Admin")
      val userUpdateRecords: Seq[DynDoc] = BWMongoDB3.user_updates.
        find(if (isAdmin) Map.empty[String, AnyRef] else Map("person_id" -> personOid))
      val outputRecords = userUpdateRecords.map(rec => {
        val author: DynDoc = BWMongoDB3.persons.find(Map("_id" -> rec.person_id[ObjectId])).head
        rec.full_name = s"${author.first_name[String]} ${author.last_name[String]}"
        rec.date_time = dateTimeString(rec.timestamp[Long], Some(timezone))
        val docOids: Seq[ObjectId] = rec.attachments[Many[ObjectId]]
        rec.links = docOids.map(docOid => {
          val docRec: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> docOid)).head
          val version: DynDoc = docRec.versions[Many[Document]].get(0)
          val fileName = version.file_name[String]
          val timestamp = version.timestamp[Long]
          val url = s"baf/DocumentVersionDownload/$fileName?document_master_id=$docOid&timestamp=$timestamp"
          new Document(Map("file_name" -> fileName, "url" -> url))
        })
        rec
      })
      response.getWriter.println(outputRecords.map(b => bson2json(b.asDoc)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }
}
