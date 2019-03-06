package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class DocumentVersionList extends HttpServlet with HttpUtils with DateTimeUtils {

  private def versions(doc: DynDoc): Seq[DynDoc] = {
    val allVersions: Seq[DynDoc] =
      if (doc.has("versions")) doc.versions[Many[Document]] else Seq.empty[DynDoc]
    allVersions.filter(_.has("timestamp"))
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val documentOid = new ObjectId(parameters("document_id"))
      val docRecord: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).head
      val goodVersions = versions(docRecord).sortWith(_.timestamp[Long] < _.timestamp[Long])
      val versionProperties: Seq[Document] = goodVersions.map(version => {
        val authorName = if (version.has("author_person_id")) {
          val authorOid = version.author_person_id[ObjectId]
          val author: DynDoc = BWMongoDB3.persons.find(Map("_id" -> authorOid)).head
          s"${author.first_name[String]} ${author.last_name[String]}"
        } else
          "NA"
        val fileName = if (version.has("file_name")) version.file_name[String] else docRecord.name[String]
        val prop: Document = Map("filename" -> fileName, "author" -> authorName,
          "timestamp" -> version.timestamp[Long], "comments" -> version.comments[String],
          "datetime" -> dateTimeString(version.timestamp[Long], Some(user.tz[String])))
        prop
      })
      response.getWriter.print(versionProperties.map(document => bson2json(document)).mkString("[", ", ", "]"))
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