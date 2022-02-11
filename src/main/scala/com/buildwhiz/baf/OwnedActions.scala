package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.jdk.CollectionConverters._

class OwnedActions extends HttpServlet with HttpUtils {

  private def docList(project: DynDoc, docIds: Seq[ObjectId], createdAfter: Long): Many[Document] = {
    val docs: Seq[DynDoc] = docIds.map(id =>BWMongoDB3.document_master.find(Map("_id" -> id)).head)
    for (doc <- docs) {
      val isReady = if (project has "documents") {
        project.documents[Many[Document]].exists(d => d.document_id[ObjectId] == doc._id[ObjectId] &&
          d.timestamp[Long] > createdAfter)
      } else {
        false
      }
      doc.is_ready = isReady
    }
    docs.map(_.asDoc)
  }.asJava

  private def activityOidToPhaseAndProject(activityOid: ObjectId): (DynDoc, DynDoc) = {
    val phase: DynDoc = BWMongoDB3.processes.find(Map("activity_ids" -> activityOid)).head
    val phaseOid = phase._id[ObjectId]
    val project = BWMongoDB3.projects.find(Map("process_ids" -> phaseOid)).head
    (phase, project)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val activityOid = new ObjectId(parameters("activity_id"))
      val activity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).head
      val actionOrder = Map("prerequisite" -> 1, "main" -> 2, "review" -> 3)
      val sortedActions: Seq[DynDoc] = activity.actions[Many[Document]].
        sortWith((a, b) => actionOrder(a.`type`[String]) < actionOrder(b.`type`[String]))
      val (phase, project) = activityOidToPhaseAndProject(activityOid)
      for (action <- sortedActions) {
        action.review_ok = ""
        action.display_status = action.status[String]
        val isWaiting = action.status[String] == "waiting"
        val assigneeIsUser = action.assignee_person_id[ObjectId] == personOid
        val phaseManagerIsUser = phase.admin_person_id[ObjectId] == personOid
        action.assignee_is_user = assigneeIsUser
        if (isWaiting) {
          action.displayDetails = assigneeIsUser
          if (!assigneeIsUser)
            action.display_status = "waiting2"
        } else {
          action.displayDetails = false
        }
        val isRelevant = assigneeIsUser | phaseManagerIsUser
        action.is_relevant = isRelevant
        if (isRelevant) {
          val p0 = if (project.has("timestamps") && project.timestamps[Document].has("start"))
              project.timestamps[Document].y.start[Long] else Long.MaxValue
          action.inDocuments = docList(project, action.inbox[Many[ObjectId]], p0)
          val t0 = if (action.has("timestamps") && action.timestamps[Document].has("start"))
              action.timestamps[Document].y.start[Long] else Long.MaxValue
          val outDocumentsOids: Seq[ObjectId] =
            if (assigneeIsUser) {
              rfiRequestOid +: submittalOid +: action.outbox[Many[ObjectId]]
            } else if (phaseManagerIsUser && action.inbox[Many[ObjectId]].contains(rfiRequestOid)) {
              Seq(rfiResponseOid)
            } else
              Nil
          val outDocs = docList(project, outDocumentsOids, t0)
          action.outDocuments = outDocs
          action.is_ready = (action.`type`[String] == "review") ||
            outDocs.forall(doc => doc.is_ready[Boolean] || doc._id[ObjectId] == rfiRequestOid)
        }
        action.remove("inbox")
        action.remove("outbox")
      }
      response.getWriter.print(sortedActions.map(activity => bson2json(activity.asDoc)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}