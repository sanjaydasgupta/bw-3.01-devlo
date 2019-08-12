package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class RfiList extends HttpServlet with HttpUtils with DateTimeUtils {

  private def getRfiList(request: HttpServletRequest): Seq[Document] = {
    val parameters = getParameterMap(request)
    val user: DynDoc = getUser(request)
    val userOid = user._id[ObjectId]
    val mongoQuery = Seq("document_id", "activity_id", "phase_id", "project_id").map(parameters.get) match {
      case Some(documentId) +: _ =>
        Map("document.document_id" -> new ObjectId(documentId),
          "document.version" -> parameters("doc_version_timestamp").toLong)
      case None +: Some(activityId) +: _ =>
        Map("document.activity_id" -> new ObjectId(activityId))
      case None +: None +: Some(phaseId) +: _ =>
        Map("document.phase_id" -> new ObjectId(phaseId))
      case None +: None +: None +: Some(projectId) +: Nil =>
        Map("project_id" -> new ObjectId(projectId))
      case _ => throw new IllegalArgumentException(s"Mandatory parameters missing")
    }
    val allRfi: Seq[DynDoc] = if (mongoQuery.nonEmpty)
      BWMongoDB3.rfi_messages.find(mongoQuery)
    else
      BWMongoDB3.rfi_messages.find(Map("members" -> userOid))
    val rfiProperties: Seq[Document] = allRfi.map(rfi => {
      val priority = if (rfi.has("priority")) rfi.priority[String] else "LOW"
      val messages: Seq[DynDoc] = rfi.messages[Many[Document]]
      val originatorOid: ObjectId = messages.head.sender[ObjectId]
      val closeable: Boolean = userOid == originatorOid
      val timestamps: DynDoc = rfi.timestamps[Document]
      val originationTime = dateTimeString(timestamps.start[Long], Some(user.tz[String]))
      val originator: DynDoc = BWMongoDB3.persons.find(Map("_id" -> originatorOid)).head
      val originatorName = s"${originator.first_name[String]} ${originator.last_name[String]}"
      val own = originatorOid == user._id[ObjectId]
      val rfiType = if (rfi.has("rfi_type")) rfi.rfi_type[String] else "NA"
      val optionalValues: Map[String, Any] =
        Seq("document_id", "activity_id", "phase_id").map(parameters.get) match {
        case Some(documentId) +: _ =>
          Map("document_id" -> new ObjectId(documentId),
            "doc_version_timestamp" -> parameters("doc_version_timestamp").toLong)
        case None +: Some(activityId) +: _ =>
          val activityOid = new ObjectId(activityId)
          val theActivity = ActivityApi.activityById(activityOid)
          val parentProcess = ActivityApi.parentProcess(activityOid)
          Map("activity_id" -> activityOid, "activity_name" -> theActivity.name[String],
              "process_name" -> parentProcess.name[String], "process_id" -> parentProcess._id[ObjectId],
              "bpmn_name" -> theActivity.bpmn_name[String])
        case None +: None +: Some(phaseId) +: Nil =>
          val phaseOid = new ObjectId(phaseId)
          val thePhase = PhaseApi.phaseById(phaseOid)
          Map("phase_id" -> phaseOid, "phase_name" -> thePhase.name[String])
        case _ => throw new IllegalArgumentException(s"Mandatory parameters missing")
      }
      optionalValues ++ Map("_id" -> rfi._id[ObjectId].toString, "priority" -> priority,
        "subject" -> rfi.subject[String], "task" -> "???", "originator" -> originatorName, "own" -> own,
        "question" -> rfi.question[String], "state" -> rfi.status[String], "assigned_to" -> "Unknown Unknown",
        "origination_date" -> originationTime, "due_date" -> originationTime, "response_date" -> originationTime,
        "project_id" -> rfi.project_id[ObjectId].toString, "closeable" -> closeable, "rfi_type" -> rfiType)
    })
    rfiProperties
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    try {
      val rfiJsons = getRfiList(request).map(bson2json)
      response.getWriter.print(rfiJsons.mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK (${rfiJsons.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}