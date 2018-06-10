package com.buildwhiz.dot

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.mutable

class GetDocumentsSummary extends HttpServlet with HttpUtils with DateTimeUtils {

  private def getLabels(doc: Document): Seq[String] = {
    val labels1 = Seq("category", "subcategory", "keywords").
        map(doc.getOrDefault(_, "").asInstanceOf[String]).flatMap(_.split(",")).
        map(_.trim).filter(_.nonEmpty)
    (doc.getOrDefault("labels", Seq.empty[String]).asInstanceOf[Seq[String]] ++ labels1).distinct
  }

  private def getDocuments(user: DynDoc): Seq[Document] = {
    val userLabels: Seq[DynDoc] = if (user.has("labels")) user.labels[Many[Document]] else Seq.empty[DynDoc]
    val docOid2labels = mutable.Map.empty[ObjectId, mutable.Buffer[String]]
    for (label <- userLabels) {
      val labelName = label.name[String]
      val docOids: Seq[ObjectId] = label.document_ids[Many[ObjectId]]
      for (docOid <- docOids) {
        val docLabels: mutable.Buffer[String] = docOid2labels.getOrElse(docOid, mutable.Buffer.empty[String])
        docLabels.append(labelName)
        docOid2labels.put(docOid, docLabels)
      }
    }
    val docs: Seq[DynDoc] = BWMongoDB3.document_master.find(Map("project_id" -> Map("$exists" -> true)))
    val docProperties: Seq[Document] = docs.map(d => {
      val versions: Seq[DynDoc] = if (d.has("versions"))
        d.versions[Many[Document]].sortWith(_.timestamp[Long] < _.timestamp[Long])
      else
        Seq.empty[DynDoc]
      val systemLabels = getLabels(d.asDoc)
      val userLabels = docOid2labels.getOrElse(d._id[ObjectId], Seq.empty[String])
      val allLabelsCsv = (systemLabels ++ userLabels).mkString(",")
      val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> d.project_id[ObjectId])).head
      val hasVersions = versions.nonEmpty &&
        Seq("file_name", "timestamp", "author_person_id").forall(versions.last.has(_))
      val documentProperties: Document = if (hasVersions) {
        val lastVersion: DynDoc = versions.last
        val fileType = lastVersion.file_name[String].split("\\.").last
        val date = dateTimeString(lastVersion.timestamp[Long], Some(user.tz[String]))
        val authorOid = lastVersion.author_person_id[ObjectId]
        val author: DynDoc = BWMongoDB3.persons.find(Map("_id" -> authorOid)).head
        val authorName = s"${author.first_name[String]} ${author.last_name[String]}"
        Map("name" -> d.description[String], "_id" -> d._id[ObjectId].toString, "phase" -> "???",
          "labels" -> Map("system" -> systemLabels, "user" -> userLabels, "all_csv" -> allLabelsCsv),
          "type" -> fileType, "author" -> authorName, "date" -> date, "project_id" -> d.project_id[ObjectId].toString,
          "project_name" -> project.name[String], "timestamp" -> lastVersion.timestamp[Long],
          "has_versions" -> true)
      } else {
        Map("name" -> d.description[String], "_id" -> d._id[ObjectId].toString, "phase" -> "???",
          "labels" -> Map("system" -> systemLabels, "user" -> userLabels, "all_csv" -> allLabelsCsv),
          "type" -> "???", "author" -> "???", "date" -> "???", "project_id" -> d.project_id[ObjectId].toString,
          "project_name" -> project.name[String], "timestamp" -> 0L, "has_versions" -> false)
      }
      documentProperties
    })
    docProperties
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    //val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val user: DynDoc = getUser(request)
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      val allDocuments = getDocuments(freshUserRecord)
      writer.print(allDocuments.map(document => bson2json(document)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK (${allDocuments.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}