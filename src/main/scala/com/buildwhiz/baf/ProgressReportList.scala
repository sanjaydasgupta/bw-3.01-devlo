package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class ProgressReportList extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).asScala.head
      val isAdmin = person.roles[Many[String]].asScala.contains("BW-Admin")
      val userUpdateRecords: Seq[DynDoc] = BWMongoDB3.user_updates.
        find(if (isAdmin) Map.empty[String, AnyRef] else Map("person_id" -> personOid)).asScala.toSeq
      val outputRecords = userUpdateRecords.map(rec => {
        val docOids: Seq[ObjectId] = rec.attachments[ObjectIdList].asScala
        val docRecs: Seq[DynDoc] = BWMongoDB3.document_master.find(Map("_id" -> Map("$in" -> docOids))).asScala.toSeq
        val versions: Seq[DynDoc] = docRecs.map(_.versions[DocumentList].get(0)) // Only 1 version expected
        val fileNames = versions.map(_.file_name[String])
        rec.file_names = fileNames
        val timestamps = versions.map(_.timestamp[Long])
        rec.links = docOids.indices.map(i => {
          s"baf/DocumentVersionDownload/${fileNames(i)}?document_master_id=${docOids(i)}&timestamp=${timestamps(i)}"
        })
        rec.remove("versions")
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
