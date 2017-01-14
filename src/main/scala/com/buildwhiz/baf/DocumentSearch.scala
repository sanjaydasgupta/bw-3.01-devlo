package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import com.buildwhiz.{DateTimeUtils, HttpUtils}
import org.bson.types.ObjectId
import org.bson.Document

import scala.collection.JavaConverters._

class DocumentSearch extends HttpServlet with HttpUtils with DateTimeUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val tz = getUser(request).get("tz").asInstanceOf[String]
      val properties = Seq("category", "subcategory", "content", "name", "description")
      val query = (("project_id" -> project430ForestOid) +:
          properties.map(p => (p, parameters(p))).filter(kv => kv._2.nonEmpty && kv._2 != "Any")).map {
            case ("content", value) =>
              val contentType: DynDoc = BWMongoDB3.content_types_master.find(Map("type" -> value)).asScala.head
              val allExtensionTypes  = contentType.extensions[java.util.List[String]].asScala.map(_.toUpperCase).asJava
              ("content", Map("$in" -> allExtensionTypes))
            case ("name", value) => ("name", Map("$regex" -> s".*$value.*", "$options" -> "i"))
            case ("subcategory", value) => ("subcategory", Map("$regex" -> s".*$value.*", "$options" -> "i"))
            case ("description", value) => ("description", Map("$regex" -> s".*$value.*", "$options" -> "i"))
            case p => p
          }.toMap
      val docMasterRecords: Seq[DynDoc] = BWMongoDB3.document_master.find(query).asScala.toSeq
      val recsWithVersions: Seq[Map[String, AnyRef]] = docMasterRecords.flatMap(docRec => {
        val versions: Seq[DynDoc] = docRec.versions[DocumentList].reverse.
            zipWithIndex.map(t => {t._1.version = t._2; t._1})
        val clientRecords: Seq[Map[String, AnyRef]] = versions.map(version => {
          val fileName = if (version has "file_name") version.file_name[String] else docRec.name[String]
          val authorOid = version.author_person_id[ObjectId]
          val author: DynDoc = BWMongoDB3.persons.find(Map("_id" -> authorOid)).asScala.head
          val authorName = s"${author.first_name[String]} ${author.last_name[String]}"
          if (!version.has("rfi_ids")) {
            version.rfi_ids = Seq.empty[ObjectId]
          }
          Map(
            "rfi_ids" -> (if (!version.has("rfi_ids")) Seq.empty[ObjectId] else version.rfi_ids[ObjectIdList]),
            "_id" -> docRec._id[ObjectId],
            "timestamp" -> version.timestamp[Long].asInstanceOf[AnyRef],
            "name" -> docRec.name[String],
            "description" -> docRec.description[String],
            "comments" -> version.comments[String],
            "date_time" -> dateTimeString(version.timestamp[Long], Some(tz)),
            "author" -> authorName,
            "version" -> version.version[Int].asInstanceOf[AnyRef],
            "link" -> (s"baf/DocumentVersionDownload/$fileName?document_master_id=${docRec._id[ObjectId]}&" +
              s"timestamp=${version.timestamp[Long]}")
          )
        })
        clientRecords
      })
      val clientDocuments: Seq[Document] = recsWithVersions.map(rec => new Document(rec))
      val jsonString = clientDocuments.map(d => bson2json(d)).mkString("[", ", ", "]")
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
