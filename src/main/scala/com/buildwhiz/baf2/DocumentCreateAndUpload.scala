package com.buildwhiz.baf2

import java.util.{Calendar, TimeZone}

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class DocumentCreateAndUpload extends HttpServlet with HttpUtils with MailUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {

      val name = parameters("name")
      val description = parameters.getOrElse("description", "???")
      val fileType = parameters.getOrElse("type", "???")

      val projectOid = new ObjectId(parameters("project_id"))
      if (!ProjectApi.exists(projectOid))
        throw new IllegalArgumentException(s"unknown project_id: $projectOid")

      val optPhaseOid = parameters.get("phase_id") match {
        case None => None
        case Some(null) | Some("") | Some("null") => None
        case Some(phaseId) =>
          val phaseOid = new ObjectId(phaseId)
          if (!PhaseApi.exists(phaseOid))
            throw new IllegalArgumentException(s"unknown phase_id: $phaseOid")
          Some(phaseOid)
      }

      val systemTags = parameters("tags").split(",").map(_.trim).toSeq

      val user: DynDoc = getUser(request)

      val action: Option[(ObjectId, String)] = parameters.get("activity_id") match {
        case Some(id) =>
          val activityOid = new ObjectId(id)
          val theActivity: DynDoc = ActivityApi.activityById(activityOid)
          val actions: Seq[DynDoc] = ActivityApi.allActions(theActivity)
          val mainAction = actions.find(_.`type`[String] == "main").get
          val mainActionName = mainAction.name[String]
          Some((activityOid, mainActionName))
        case None => None
      }

      val category: Option[String] = parameters.get("category") match {
        case Some(str) if str.matches("(?i)required") => Some("required")
        case Some(str) if str.matches("(?i)additional") => Some("additional")
        case Some(str) if str.matches("(?i)specification") => Some("specification")
        case Some(str) if str.matches("(?i)submittal") => Some("submittal")
        case Some(str) if str.matches("(?i)contract") => Some("contract")
        case Some(other) => throw new IllegalArgumentException(s"Bad category: '$other")
        case None => if (action.isDefined)
            throw new IllegalArgumentException("Not found: category")
          else
            None
      }

      val docOid = DocumentApi.createProjectDocumentRecord(name, description, fileType, systemTags, projectOid,
          optPhaseOid, action, category)
      BWLogger.audit(getClass.getName, request.getMethod, s"Created new document $docOid", request)

      val parts = getParts(request)
      if (parts.length > 1)
        throw new IllegalArgumentException(s"multiple file uploads not allowed")
      if (parts.length == 1) {
        val part = parts.head
        val submittedFilename = part.getSubmittedFileName
        val fullFileName = if (submittedFilename == null || submittedFilename.isEmpty)
          "unknown.tmp"
        else
          submittedFilename
        val inputStream = part.getInputStream
        val comment: String = parameters.getOrElse("version_comment", "NA")
        val authorOid = parameters.get("author_id") match {
          case Some(id) => new ObjectId(id)
          case None => user._id[ObjectId]
        }
        if (!PersonApi.exists(authorOid))
          throw new IllegalArgumentException(s"unknown author-id: ${authorOid.toString}")
        val timestamp = parameters.get("timestamp") match {
          case Some(ts) =>
            val userTimezone = TimeZone.getTimeZone(user.tz[String])
            val calendar = Calendar.getInstance(userTimezone)
            val timezoneOffset = userTimezone.getOffset(calendar.getTimeInMillis)
            ts.toLong - timezoneOffset
          case None => System.currentTimeMillis
        }

        //val storageResult = DocumentApi.storeAmazonS3(fullFileName, inputStream, projectOid.toString,
        val storageResult = DocumentApi.storeDocument(fullFileName, inputStream, projectOid.toString,
          docOid, timestamp, comment, authorOid, request)

        (action, category) match {
          case (Some((activityOid, _)), Some(theCategory)) =>
            ActivityApi.addChangeLogEntry(activityOid,
              s"Document '$name' ($theCategory) created and uploaded", Some(user._id[ObjectId]))
          case (Some((activityOid, _)), None) =>
            ActivityApi.addChangeLogEntry(activityOid,
              s"Document '$name' created and uploaded", Some(user._id[ObjectId]))
          case _ =>
        }

        val message = s"Added version (${storageResult._2} bytes) to new document '$name'"
        BWLogger.audit(getClass.getName, request.getMethod, message, request)
      } else if (parts.isEmpty) {
        (action, category) match {
          case (Some((activityOid, _)), Some(theCategory)) =>
            ActivityApi.addChangeLogEntry(activityOid, s"Document '$name' ($theCategory) created",
              Some(user._id[ObjectId]))
          case (Some((activityOid, _)), None) =>
            ActivityApi.addChangeLogEntry(activityOid, s"Document '$name' created", Some(user._id[ObjectId]))
          case _ =>
        }
      }

      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

