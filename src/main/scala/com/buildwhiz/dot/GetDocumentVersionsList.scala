package com.buildwhiz.dot

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class GetDocumentVersionsList extends HttpServlet with HttpUtils with DateTimeUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val user: DynDoc = getUser(request)
      val documentOid = new ObjectId(parameters("document_id"))
      val projectOid = new ObjectId(parameters("project_id"))
      val docRecord: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> documentOid,
          "project_id" -> projectOid)).head
      val versions: Seq[DynDoc] = docRecord.versions[Many[Document]]
      val versionProperties: Seq[Document] = versions.map(version => {
        val authorOid = version.author_person_id[ObjectId]
        val author: DynDoc = BWMongoDB3.persons.find(Map("_id" -> authorOid)).head
        val authorName = s"${author.first_name[String]} ${author.last_name[String]}"
        val prop: Document = Map("filename" -> version.file_name[String], "author" -> authorName,
          "timestamp" -> version.timestamp[Long],
          "datetime" -> dateTimeString(version.timestamp[Long], Some(user.tz[String])))
        prop
      })
      writer.print(versionProperties.map(document => bson2json(document)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK (${versionProperties.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}