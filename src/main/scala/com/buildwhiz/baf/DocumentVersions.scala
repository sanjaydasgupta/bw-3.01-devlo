package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import com.buildwhiz.{DateTimeUtils, HttpUtils, MailUtils}
import org.bson.types.ObjectId
import org.bson.Document

import scala.collection.JavaConverters._

class DocumentVersions extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val docMasterOid = new ObjectId(parameters("document_master_id"))
      val docMasterRecord: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> docMasterOid)).asScala.head
      val versions: Seq[DynDoc] = docMasterRecord.versions[DocumentList].asScala
      val versions2: Seq[Document] = versions.map(version => {
        val personOid = version.author_person_id[ObjectId]
        val author: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).asScala.head
        version.author_name = author.first_name[String] + " " + author.last_name[String]
        val timestamp = version.timestamp[Long]
        version.date_time = dateTimeString(timestamp)
        val link =
          if (version has "file_name")
            s"""baf/DocumentVersionDownload/${version.file_name[String]}?document_master_id=$docMasterOid&timestamp=$timestamp"""
          else
            s"""baf/DocumentVersionDownload/${docMasterRecord.name[String]}?document_master_id=$docMasterOid&timestamp=$timestamp"""
        version.link = link
        version.asDoc
      })
      val jsonString = versions2.map(bson2json).mkString("[", ", ", "]")
      response.getOutputStream.println(jsonString)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doPost", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
