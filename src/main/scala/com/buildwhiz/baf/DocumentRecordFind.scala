package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.jdk.CollectionConverters._

class DocumentRecordFind extends HttpServlet with HttpUtils with DateTimeUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val tz = getUser(request).get("tz").asInstanceOf[String]
      val properties = Seq("category", "subcategory", "content", "name", "description")
      val query = (("project_id" -> project430ForestOid) +:
          properties.map(p => (p, parameters(p))).filter(kv => kv._2.nonEmpty && kv._2 != "Any")).map {
            case ("content", value) =>
              val contentType: DynDoc = BWMongoDB3.content_types_master.find(Map("type" -> value)).head
              val allExtensionTypes  = contentType.extensions[java.util.List[String]].map(_.toUpperCase).asJava
              ("content", Map("$in" -> allExtensionTypes))
            case ("name", value) => ("name", Map("$regex" -> s".*$value.*", "$options" -> "i"))
            case ("subcategory", value) => ("subcategory", Map("$regex" -> s".*$value.*", "$options" -> "i"))
            case ("description", value) => ("description", Map("$regex" -> s".*$value.*", "$options" -> "i"))
            case p => p
          }.toMap
      val docMasterRecords: Seq[DynDoc] = BWMongoDB3.document_master.find(query)
      val recsWithLinks = docMasterRecords.map(docMaster => {
        val versions: Seq[DynDoc] = docMaster.versions[Many[Document]]
        if (versions.nonEmpty) {
          val latestVersion: DynDoc = versions.last
          val timestamp = latestVersion.timestamp[Long]
          docMaster.timestamp = dateTimeString(timestamp, Some(tz))
          val fileName = if (latestVersion has "file_name") latestVersion.file_name[String] else docMaster.name[String]
          docMaster.link = s"baf/DocumentVersionDownload/$fileName?document_master_id=${docMaster._id[ObjectId]}&" +
            s"timestamp=$timestamp"
        } else {
          docMaster.timestamp = ""
        }
        docMaster
      })
      val jsonString = recsWithLinks.map(d => bson2json(d.asDoc)).mkString("[", ", ", "]")
      response.getOutputStream.println(jsonString)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doPost", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
