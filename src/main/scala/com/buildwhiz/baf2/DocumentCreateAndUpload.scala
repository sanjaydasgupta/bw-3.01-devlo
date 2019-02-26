package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
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
        throw new IllegalArgumentException(s"unknown project-id: ${projectOid.toString}")

      val systemTags = parameters("tags").split(",").toSeq

      val user: DynDoc = getUser(request)
      val authorOid = parameters.get("author_id") match {
        case Some(id) => new ObjectId(id)
        case None => user._id[ObjectId]
      }
      if (!PersonApi.exists(authorOid))
        throw new IllegalArgumentException(s"unknown author-id: ${authorOid.toString}")

      val timestamp = parameters.get("timestamp") match {
        case Some(ts) => ts.toLong
        case None => System.currentTimeMillis
      }

      val action: Option[(ObjectId, String)] = parameters.get("activity_id") match {
        case Some(id) => parameters.get("action_name") match {
          case Some(actionName) =>
            val activityOid = new ObjectId(id)
            val theActivity: DynDoc = ActivityApi.activityById(activityOid)
            val actions: Seq[DynDoc] = theActivity.actions[Many[Document]]
            if (!actions.exists(_.name[String] == actionName))
              throw new IllegalArgumentException(s"unknown task: '$actionName'")
            Some((activityOid, actionName))
          case None => throw new IllegalArgumentException("action_name not provided")
        }
        case None => None
      }

      val category: Option[String] = parameters.get("category") match {
        case Some(str) if str.matches("(?i)required") => Some("required")
        case Some(str) if str.matches("(?i)additional") => Some("additional")
        case Some(other) => throw new IllegalArgumentException(s"Bad category: '$other")
        case None => if (action.isDefined)
            throw new IllegalArgumentException("Not found: category")
          else
            None
      }

      val docOid = DocumentApi.createProjectDocumentRecord(name, description, fileType, systemTags, projectOid, None,
          action, category)
      BWLogger.audit(getClass.getName, request.getMethod, s"Created new document $docOid", request)

      val partCount = if (request.getContentType.contains("multipart")) request.getParts.size else 0
      if (partCount > 1)
        throw new IllegalArgumentException(s"multiple file uploads not allowed")
      if (partCount == 1) {
        val part = request.getParts.iterator.next()
        val submittedFilename = part.getSubmittedFileName
        val fullFileName = if (submittedFilename == null || submittedFilename.isEmpty)
          "unknown.tmp"
        else
          submittedFilename
        val inputStream = part.getInputStream
        val comment: String = parameters.getOrElse("version_comment", "NA")
        val storageResult = DocumentApi.storeAmazonS3(fullFileName, inputStream, projectOid.toString,
          docOid, timestamp, comment, authorOid, request)
        val message = s"Added version (${storageResult._2} bytes) to new document '$name'"
        BWLogger.audit(getClass.getName, request.getMethod, message, request)
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

