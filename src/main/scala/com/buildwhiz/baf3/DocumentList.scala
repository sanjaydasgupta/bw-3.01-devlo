package com.buildwhiz.baf3

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId
import com.buildwhiz.baf2.{DocumentApi, PersonApi, PhaseApi, ProjectApi}

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
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
    val docRecords: Seq[DynDoc] = DocumentApi.documentList3(request)
    val phaseOid2NameMap: Map[ObjectId, String] = docRecords.flatMap(_.get[ObjectId]("phase_id")).distinct.
        map(oid => (oid, PhaseApi.phaseById(oid).name[String])).toMap
    val projectName = docRecords.headOption match {
      case Some(doc) => ProjectApi.projectById(doc.project_id[ObjectId]).name[String]
      case None => "-" // Never used
    }
    val docProperties: Seq[Document] = docRecords.map(doc => {
      val fileExtension = doc.get[String]("file_extension") match {
        case Some(fileExt) => fileExt
        case None => "NA"
      }
      val versions: Seq[DynDoc] = DocumentApi.versions(doc)
      val systemLabels = DocumentApi.getSystemTags(doc)
      val phaseName = doc.get[ObjectId]("phase_id") match {
        case Some(phaseOid) => phaseOid2NameMap(phaseOid)
        case None => "NA"
      }
      val versionCount = versions.length
      val documentProperties: Document = if (versionCount != 0) {
        val lastVersion: DynDoc = versions.sortWith(_.timestamp[Long] < _.timestamp[Long]).last
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
        Map("name" -> doc.name[String], "_id" -> doc._id[ObjectId].toString, "phase_name" -> phaseName,
          "team" -> "NA", "category" -> "NA", "tags" -> systemLabels, "type" -> fileExtension,
          "uploaded_by" -> authorName, "date" -> date, "version_count" -> versionCount, "size" -> fileSize)
      } else {
        Map("name" -> doc.name[String], "_id" -> doc._id[ObjectId].toString, "phase_name" -> phaseName,
          "team" -> "NA", "category" -> "NA", "tags" -> systemLabels, "type" -> fileExtension,
          "uploaded_by" -> "NA", "date" -> "NA", "version_count" -> versionCount, "size" -> "NA")
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
      val canManage = parameters.get("phase_id") match {
        case Some(phaseId) => PhaseApi.canManage(userOid, PhaseApi.phaseById(new ObjectId(phaseId)))
        case None => ProjectApi.canManage(userOid, ProjectApi.projectById(projectOid))
      }
      val canDelete = canManage && user.first_name[String] == "Prabhas"
      val allDocuments = getDocuments(user, request).asJava
      //val gDriveUrl = PersonApi.userGDriveFolderUrl(user)
      val result = new Document("document_list", allDocuments).append("can_rename", canManage).
          append("can_delete", canDelete).append("can_add", true)//.append("g_drive_url", gDriveUrl)
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