package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class DocumentList extends HttpServlet with HttpUtils with DateTimeUtils {

  private def i2s(i: Long): String = {
    if (i < 1000)
      f"$i%d bytes"
    else if (i < 1000000L)
      f"${(i / 100) / 10.0}%3.1f kB"
    else if (i < 1000000000L)
      f"${(i / 100000L) / 10.0}%3.1f MB"
    else
      f"${(i / 100000000L) / 10.0}%3.1f GB"
  }

  private def getDocuments(user: DynDoc, request: HttpServletRequest): Seq[Document] = {
    val docOid2labels: Map[ObjectId, Seq[String]] = DocumentApi.docOid2UserTags(user)
    val docRecords: Seq[DynDoc] = DocumentApi.documentList(request)
    val docProperties: Seq[Document] = docRecords.map(d => {
      val versions: Seq[DynDoc] = DocumentApi.versions(d)
      val systemLabels = DocumentApi.getSystemTags(d)
      val userLabels = docOid2labels.getOrElse(d._id[ObjectId], Seq.empty[String])
      val logicalLabels = DocumentApi.getLogicalTags(systemLabels ++ userLabels, user)
      val allUserLabels = userLabels ++ logicalLabels
      val allLabelsCsv = (systemLabels ++ allUserLabels).mkString(",")
      val project = ProjectApi.projectById(d.project_id[ObjectId])
      val phaseName = if (d.has("phase_id")) {
        val thePhase = PhaseApi.phaseById(d.phase_id[ObjectId])
        thePhase.name[String]
      } else {
        "NA"
      }
      val taskName = if (d.has("activity_id")) {
        if (ActivityApi.exists(d.activity_id[ObjectId])) {
          val theActivity = ActivityApi.activityById(d.activity_id[ObjectId])
          theActivity.name[String]
        } else {
          "NA"
        }
      } else {
        "NA"
      }
      val versionCount = versions.length
      val documentProperties: Document = if (versionCount != 0) {
        val lastVersion: DynDoc = versions.sortWith(_.timestamp[Long] < _.timestamp[Long]).last
        val fileType = lastVersion.file_name[String].split("\\.").last
        val fileSize = if (lastVersion.has("size"))
          i2s(lastVersion.size)
        else
          "NA"
        val date = dateTimeString(lastVersion.timestamp[Long], Some(user.tz[String]))
        val authorOid = lastVersion.author_person_id[ObjectId]
        val authorName: String = if (PersonApi.exists(authorOid)) {
          PersonApi.fullName(PersonApi.personById(authorOid))
        } else {
          "Unknown Unknown"
        }
        Map("name" -> d.name[String], "_id" -> d._id[ObjectId].toString, "phase" -> phaseName,
          "labels" -> Map("system" -> systemLabels, "user" -> allUserLabels, "all_csv" -> allLabelsCsv),
          "type" -> fileType, "author" -> authorName, "date" -> date, "project_id" -> d.project_id[ObjectId].toString,
          "project_name" -> project.name[String], "timestamp" -> lastVersion.timestamp[Long],
          "has_versions" -> true, "version_count" -> versionCount, "size" -> fileSize, "task_name" -> taskName)
      } else {
        Map("name" -> d.name[String], "_id" -> d._id[ObjectId].toString, "phase" -> phaseName,
          "labels" -> Map("system" -> systemLabels, "user" -> allUserLabels, "all_csv" -> allLabelsCsv),
          "type" -> "NA", "author" -> "NA", "date" -> "NA", "project_id" -> d.project_id[ObjectId].toString,
          "project_name" -> project.name[String], "timestamp" -> 0L, "has_versions" -> false,
          "version_count" -> versionCount, "size" -> "NA", "task_name" -> taskName)
      }
      documentProperties
    })
    docProperties.groupBy(_.getString("_id")).toSeq.map(_._2.head)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      val freshUserRecord: DynDoc = PersonApi.personById(userOid)
      val canRename = ProjectApi.canManage(userOid, ProjectApi.projectById(projectOid))
      val canDelete = freshUserRecord.first_name[String] == "Prabhas"
      val allDocuments = getDocuments(freshUserRecord, request).asJava
      val gDriveUrl = PersonApi.userGDriveFolderUrl(freshUserRecord)
      val result = new Document("document_list", allDocuments).append("can_rename", canRename).
          append("can_delete", canDelete).append("can_add", true).
          append("g_drive_url", gDriveUrl)
      response.getWriter.print(result.toJson)
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