package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class TaskDocumentInfo extends HttpServlet with HttpUtils with DateTimeUtils {

  private def taskAssociatedDocuments(user: DynDoc, activity: DynDoc, actionName: String): Seq[DynDoc] = {
    val documentRecords: Seq[DynDoc] = BWMongoDB3.document_master.
        find(Map("activity_id" -> activity._id[ObjectId], "action_name" -> actionName,
        "category" -> Map("$exists" -> true)))
    documentRecords
  }

  private def uiFormattedRecords(user: DynDoc, documents: Seq[DynDoc]): Seq[Document] = {
    documents.map(document => {
      val versions: Seq[DynDoc] = document.versions[Many[Document]]
      val currentVersion: DynDoc = versions.last
      val timezone = user.tz[String]
      val versionTimestamp = currentVersion.timestamp[Long]
      val versionDate = dateTimeString(versionTimestamp, Some(timezone))
      val fileName = currentVersion.file_name[String]
      val fileType = if (fileName.contains(".")) fileName.split("\\.").last else "???"
      val documentOid = document._id[ObjectId]
      val downloadUrl = s"baf2/DocumentVersionDownload?document_master_id=$documentOid" +
          s"&timestamp=$versionTimestamp"
      new Document("name", document.name[String]).append("type", fileType).append("version_date", versionDate).
          append("version_count", versions.length).append("download_url", downloadUrl).append("_id", documentOid).
          append("timestamp", versionTimestamp)
    })
  }

  private def checkListItems(user: DynDoc, activity: DynDoc, actionName: String, theAction: DynDoc): Seq[Document] = {
    if (activity.has("check_list")) {
      activity.check_list[Many[Document]]
    } else
      Seq.empty[Document]
  }

  private def taskDocumentInfoRecord(user: DynDoc, activity: DynDoc, actionName: String, action: DynDoc,
      process: DynDoc, phase: DynDoc, project: DynDoc): String = {
    val documents = taskAssociatedDocuments(user, activity, actionName)
    val checkList = checkListItems(user, activity, actionName, action)
    val requiredDocuments = uiFormattedRecords(user, documents.filter(_.category[String] == "required"))
    val additionalDocuments = uiFormattedRecords(user, documents.filter(_.category[String] == "additional"))
    val specificationDocuments = uiFormattedRecords(user, documents.filter(_.category[String] == "specification"))
    val specificationUrl = specificationDocuments.headOption match {
      case None => ""
      case Some(doc) => doc.get("download_url").asInstanceOf[String]
    }
    val enableAddButtons = PersonApi.isBuildWhizAdmin(user._id[ObjectId]) ||
        ProcessApi.canManage(user._id[ObjectId], process)
    val record = new Document("task_specification_url", specificationUrl).append("check_list", checkList).
        append("required_documents", requiredDocuments).append("additional_documents", additionalDocuments).
        append("enable_add_buttons", enableAddButtons)
    bson2json(record)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val activityOid = new ObjectId(parameters("activity_id"))
      val theActivity = ActivityApi.activityById(activityOid)
      val actions = ActivityApi.allActions(theActivity)
//      val actionName = parameters("action_name")
      val theAction = actions.find(_.`type`[String] == "main") match {
        case Some(a) => a
        case None => throw new IllegalArgumentException(s"Could not find 'main' action")
      }
      val actionName = theAction.name[String]
      val parentProcess = ActivityApi.parentProcess(activityOid)
      val ancestorPhase = ProcessApi.parentPhase(parentProcess._id[ObjectId])
      val ancestorProject = PhaseApi.parentProject(ancestorPhase._id[ObjectId])
      val user: DynDoc = getUser(request)
      val freshUserRecord = PersonApi.personById(user._id[ObjectId])
      response.getWriter.print(taskDocumentInfoRecord(freshUserRecord, theActivity, actionName, theAction,
          parentProcess, ancestorPhase, ancestorProject))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}