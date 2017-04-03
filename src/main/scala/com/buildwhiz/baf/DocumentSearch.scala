package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.types.ObjectId
import org.bson.Document

import scala.collection.JavaConverters._

class DocumentSearch extends HttpServlet with HttpUtils with DateTimeUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val tz = getUser(request).get("tz").asInstanceOf[String]
      val queryPropertyNames = Set("category", "subcategory", "content", "name", "description", "author_person_id")
      val query = (("project_id" -> project430ForestOid) +:
          parameters.toSeq.filter(p => queryPropertyNames.contains(p._1)).
            filter(kv => kv._2.nonEmpty && kv._2 != "Any" && kv._1 != "author_person_id")).map {
            case ("content", value) =>
              val contentType: DynDoc = BWMongoDB3.content_types_master.find(Map("type" -> value)).head
              val allExtensionTypes  = contentType.extensions[java.util.List[String]].map(_.toUpperCase).asJava
              ("content", Map("$in" -> allExtensionTypes))
            case ("name", value) => ("name", Map("$regex" -> s".*$value.*", "$options" -> "i"))
            case ("subcategory", value) => ("subcategory", Map("$regex" -> s".*$value.*", "$options" -> "i"))
            case ("description", value) => ("description", Map("$regex" -> s".*$value.*", "$options" -> "i"))
            //case ("author_person_id", value: String) => ("versions.0.author_person_id", new ObjectId(value))
            case p => p
          }.toMap
      val allRecords: Seq[DynDoc] = BWMongoDB3.document_master.find(query)
      val docRecords = allRecords.filter(_.category[String] != "SYSTEM")
      val recsWithVersions: Seq[Map[String, AnyRef]] = docRecords.flatMap(docRec => {
        val allVersions: Seq[DynDoc] = docRec.versions[Many[Document]].sortBy(d => -d.timestamp[Long]).
            zipWithIndex.map(t => {t._1.version = t._2; t._1})
        val versions = if (parameters.contains("versions") && parameters("versions") == "latest" && allVersions.length > 1)
          Seq(allVersions.head)
        else
          allVersions
        val clientRecords: Seq[Map[String, AnyRef]] = versions.map(version => {
          val fileName = if (version has "file_name") version.file_name[String] else docRec.name[String]
          val authorOid = version.author_person_id[ObjectId]
          val author: DynDoc = BWMongoDB3.persons.find(Map("_id" -> authorOid)).head
          val authorName = s"${author.first_name[String]} ${author.last_name[String]}"
          if (!version.has("rfi_ids")) {
            version.rfi_ids = Seq.empty[ObjectId]
          }
          Map(
            "rfi_ids" -> (if (!version.has("rfi_ids")) Seq.empty[ObjectId] else version.rfi_ids[Many[ObjectId]]),
            "_id" -> docRec._id[ObjectId],
            "timestamp" -> version.timestamp[Long].asInstanceOf[AnyRef],
            "category" -> docRec.category[String],
            "subcategory" -> docRec.subcategory[String],
            "name" -> docRec.name[String],
            "description" -> (if (docRec.has("description")) docRec.description[String] else "-"),
            "comments" -> (if (version.has("comments")) version.comments[String] else "-"),
            "date_time" -> dateTimeString(version.timestamp[Long], Some(tz)),
            "author" -> authorName,
            "author_person_id" -> version.author_person_id[ObjectId],
            "version" -> version.version[Int].asInstanceOf[AnyRef],
            "link" -> (s"baf/DocumentVersionDownload/$fileName?document_master_id=${docRec._id[ObjectId]}&" +
              s"timestamp=${version.timestamp[Long]}")
          )
        })
        clientRecords
      })
      val recsWithVersions2 = if (parameters.contains("author_person_id"))
        recsWithVersions.filter(_("author_person_id") == new ObjectId(parameters("author_person_id")))
      else
        recsWithVersions
      val clientDocuments: Seq[Document] = recsWithVersions2.map(rec => new Document(rec))
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
