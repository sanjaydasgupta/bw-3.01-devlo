package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class RfiList extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    try {
      val parameters = getParameterMap(request)
      val params: Seq[Option[String]] =
          Seq("document_id", "doc_version_timestamp", "activity_id", "phase_id", "project_id").
          map(parameters.get)
      val user: DynDoc = getUser(request)
      val rfiJsons = RfiList.getRfiList(user, None, params.head, params(1), params(2), params(3), params(4)).map(bson2json)
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

object RfiList extends DateTimeUtils with HttpUtils {

  def getRfiList(user: DynDoc, optRequest: Option[HttpServletRequest], optDocumentId: Option[String] = None,
      optDocumentVerTs: Option[String] = None, optActivityId: Option[String] = None, optPhaseId: Option[String] = None,
      optProjectId: Option[String] = None, doLog: Boolean = false): Seq[Document] = {
    (doLog, optRequest) match {
      case (true, Some(request)) =>
        BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
      case _ =>
    }
    val userOid = user._id[ObjectId]
    val mongoQuery: Map[String, Any] =
        (optDocumentId, optDocumentVerTs, optActivityId, optPhaseId, optProjectId) match {
      case (Some(documentId), Some(documentVerTs), _, _, _) =>
        Map("document.document_id" -> new ObjectId(documentId), "document.version" -> documentVerTs)
      case (_, _, Some(activityId), _, _) => Map("document.activity_id" -> new ObjectId(activityId))
      case (_, _, _, Some(phaseId), _) => Map("document.phase_id" -> new ObjectId(phaseId))
      case (_, _, _, _, Some(projectId: String)) => Map("project_id" -> new ObjectId(projectId))
      case _ => Map("members" -> userOid)
    }
    val allRfi: Seq[DynDoc] = BWMongoDB3.rfi_messages.find(mongoQuery)
    val rfiProperties: Seq[Document] = allRfi.map(rfi => {
      val priority = if (rfi.has("priority")) rfi.priority[String] else "LOW"
      val messages: Seq[DynDoc] = rfi.messages[Many[Document]]
      val originatorOid: ObjectId = messages.head.sender[ObjectId]
      val closeable: Boolean = userOid == originatorOid
      val timestamps: DynDoc = rfi.timestamps[Document]
      val originationTime = dateTimeString(timestamps.start[Long], Some(user.tz[String]))
      val originator: DynDoc = BWMongoDB3.persons.find(Map("_id" -> originatorOid)).head
      val originatorName = PersonApi.fullName(originator)
      val own = originatorOid == user._id[ObjectId]
      val rfiType = if (rfi.has("rfi_type")) rfi.rfi_type[String] else "NA"
      val referenceType = rfi.document[Document].y.id_type[String]

      def reference(name: String): Option[AnyRef] = {
        val refs = rfi.document[Document]
        if (refs.containsKey(name))
          Some(refs.get(name))
        else
          None
      }

      val optionalValues: Map[String, Any] =
        Seq("document_id", "activity_id", "phase_id").map(reference) match {
          case Some(documentOid: ObjectId) +: _ =>
            Map("document_id" -> documentOid.toString, "doc_version_timestamp" -> rfi.document[Document].y.version[Long])
          case None +: Some(activityOid: ObjectId) +: _ =>
            val theActivity = ActivityApi.activityById(activityOid)
            val parentProcess = ActivityApi.parentProcess(activityOid)
            val parentPhase = ProcessApi.parentPhase(parentProcess._id[ObjectId])
            Map("activity_id" -> activityOid.toString, "activity_name" -> theActivity.name[String],
              "process_name" -> parentProcess.name[String], "process_id" -> parentProcess._id[ObjectId],
              "bpmn_name" -> theActivity.bpmn_name[String], "phase_id" -> parentPhase._id[ObjectId].toString,
              "phase_name" -> parentPhase.name[String])
          case None +: None +: Some(phaseOid: ObjectId) +: _ =>
            val thePhase = PhaseApi.phaseById(phaseOid)
            Map("phase_id" -> phaseOid.toString, "phase_name" -> thePhase.name[String])
          case _ => Map.empty[String, Any]
        }
      val theProject = ProjectApi.projectById(rfi.project_id[ObjectId])
      optionalValues ++ Map("_id" -> rfi._id[ObjectId].toString, "priority" -> priority,
        "subject" -> rfi.subject[String], "task" -> "???", "originator" -> originatorName, "own" -> own,
        "question" -> rfi.question[String], "state" -> rfi.status[String], "assigned_to" -> "Unknown Unknown",
        "origination_date" -> originationTime, "due_date" -> originationTime, "response_date" -> originationTime,
        "project_id" -> rfi.project_id[ObjectId].toString, "project_name" -> theProject.name[String],
        "closeable" -> closeable, "rfi_type" -> rfiType,
        "reference_type" -> referenceType.split("_")(0))
    })
    (doLog, optRequest) match {
      case (true, Some(request)) =>
        BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK (${rfiProperties.length})", request)
      case _ =>
    }
    rfiProperties
  }

}