package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class DocumentList extends HttpServlet with HttpUtils with DateTimeUtils {

  private def getDocuments(user: DynDoc, request: HttpServletRequest): Seq[Document] = {
    val docOid2labels: Map[ObjectId, Seq[String]] = DocumentApi.docOid2UserTags(user)
    val docRecords: Seq[DynDoc] = DocumentApi.documentsByProjectId(request)
    val docProperties: Seq[Document] = docRecords.map(d => {
      val versions: Seq[DynDoc] = DocumentApi.versions(d)
      val systemLabels = DocumentApi.getSystemTags(d)
      val userLabels = docOid2labels.getOrElse(d._id[ObjectId], Seq.empty[String])
      val logicalLabels = DocumentApi.getLogicalTags(systemLabels ++ userLabels, user)
      val allUserLabels = userLabels ++ logicalLabels
      val allLabelsCsv = (systemLabels ++ allUserLabels).mkString(",")
      val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> d.project_id[ObjectId])).head
      val versionCount = versions.length
      val documentProperties: Document = if (versionCount != 0) {
        val lastVersion: DynDoc = versions.sortWith(_.timestamp[Long] < _.timestamp[Long]).last
        val fileType = lastVersion.file_name[String].split("\\.").last
        val fileSize = lastVersion.asDoc.getOrDefault("size", "???").toString
        val date = dateTimeString(lastVersion.timestamp[Long], Some(user.tz[String]))
        val authorOid = lastVersion.author_person_id[ObjectId]
        val authorName: String = BWMongoDB3.persons.find(Map("_id" -> authorOid)).headOption match {
          case None => "Unknown Unknown"
          case Some(author) => s"${author.first_name[String]} ${author.last_name[String]}"
        }
        Map("name" -> d.name[String], "_id" -> d._id[ObjectId].toString, "phase" -> "???",
          "labels" -> Map("system" -> systemLabels, "user" -> allUserLabels, "all_csv" -> allLabelsCsv),
          "type" -> fileType, "author" -> authorName, "date" -> date, "project_id" -> d.project_id[ObjectId].toString,
          "project_name" -> project.name[String], "timestamp" -> lastVersion.timestamp[Long],
          "has_versions" -> true, "version_count" -> versionCount, "size" -> fileSize)
      } else {
        Map("name" -> d.name[String], "_id" -> d._id[ObjectId].toString, "phase" -> "???",
          "labels" -> Map("system" -> systemLabels, "user" -> allUserLabels, "all_csv" -> allLabelsCsv),
          "type" -> "???", "author" -> "???", "date" -> "???", "project_id" -> d.project_id[ObjectId].toString,
          "project_name" -> project.name[String], "timestamp" -> 0L, "has_versions" -> false,
          "version_count" -> versionCount, "size" -> "???")
      }
      documentProperties
    })
    docProperties.groupBy(_.getString("_id")).toSeq.map(_._2.head)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    //val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      val allDocuments = getDocuments(freshUserRecord, request)
      response.getWriter.print(allDocuments.map(document => bson2json(document)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${allDocuments.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}