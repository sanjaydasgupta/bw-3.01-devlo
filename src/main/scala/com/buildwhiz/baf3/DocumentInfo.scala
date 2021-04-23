package com.buildwhiz.baf3

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId
import com.buildwhiz.baf2.{ActivityApi, PersonApi, PhaseApi, ProjectApi}

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class DocumentInfo extends HttpServlet with HttpUtils with DateTimeUtils {

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

  private def versionInformation(doc: DynDoc, user: DynDoc): Seq[Document] = {
    val rawVersions: Seq[DynDoc] = if (doc.has("versions")) {
      doc.versions[Many[Document]]
    } else {
      Seq.empty[DynDoc]
    }
    rawVersions.sortWith(_.timestamp[Long] < _.timestamp[Long]).map(version => {
      val authorName = if (version.has("author_person_id")) {
        val authorOid = version.author_person_id[ObjectId]
        val author: DynDoc = BWMongoDB3.persons.find(Map("_id" -> authorOid)).head
        PersonApi.fullName(author)
      } else {
        "NA"
      }
      val fileSize = if (version.has("size"))
        i2s(version.size[Long])
      else
        "NA"
      val prop: Document = Map("author" -> authorName, "comments" -> version.comments[String],
        "datetime" -> dateTimeString(version.timestamp[Long], Some(user.tz[String])), "size" -> fileSize)
      prop
    })
  }

  private def wrap(value: Any, editable: Boolean): DynDoc = {
    new Document("editable", editable).append("value", value.toString)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val documentOid = new ObjectId(parameters("document_id"))
      val docRecord: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).head
      val projectOid = docRecord.project_id[ObjectId]
      val editable = PersonApi.isBuildWhizAdmin(Right(user)) ||
          ProjectApi.canManage(user._id[ObjectId], ProjectApi.projectById(projectOid))
      val phaseName = docRecord.get[ObjectId]("phase_id") match {
        case Some(phaseOid) => PhaseApi.phaseById(phaseOid).name[String]
        case None => "NA"
      }
      val taskName = docRecord.get[ObjectId]("activity_id") match {
        case Some(activityOid) => ActivityApi.activityById(activityOid).name[String]
        case None => "NA"
      }
      val deliverableName = docRecord.get[ObjectId]("deliverable_id") match {
        case Some(activityOid) => DeliverableApi.deliverableById(activityOid).name[String]
        case None => "NA"
      }
      val tags = docRecord.get[Many[String]]("labels") match {
        case Some(t) => t.toSeq.mkString(", ")
        case None => "NA"
      }
      val versionInfo: Seq[Document] = versionInformation(docRecord, user)
      val returnDoc: Document = Map("deliverable_name" -> wrap(deliverableName, editable = false),
        "name" -> wrap(docRecord.name[String], editable = editable),
        "phase_name" -> wrap(phaseName, editable = false),
        "task_name" -> wrap(taskName, editable = false), "tags" -> wrap(tags, editable = false),
        "versions" -> versionInfo)
      response.getWriter.print(returnDoc.toJson)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK (${versionInfo.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}