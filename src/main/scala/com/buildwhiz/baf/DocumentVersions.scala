package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import com.buildwhiz.{HttpUtils, MailUtils}
import org.bson.types.ObjectId
import org.bson.Document
import java.util.Calendar

import scala.collection.JavaConverters._

class DocumentVersions extends HttpServlet with HttpUtils with MailUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val docMasterOid = new ObjectId(parameters("document_master_id"))
      val record: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> docMasterOid)).asScala.head
      val versions: Seq[DynDoc] = record.versions[DocumentList].asScala
      val versions2: Seq[Document] = versions.map(v => {
        val personOid = v.author_person_id[ObjectId]
        val author: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).asScala.head
        v.author_name = author.first_name[String] + " " + author.last_name[String]
        val cal = Calendar.getInstance()
        cal.setTimeInMillis(v.timestamp[Long])
        v.timestamp = s"${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}-${cal.get(Calendar.DAY_OF_MONTH)}"
        v.asDoc
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
