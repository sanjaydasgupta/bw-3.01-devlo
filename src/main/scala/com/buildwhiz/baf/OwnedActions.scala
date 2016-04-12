package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3, Utils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConversions._

class OwnedActions extends HttpServlet with Utils {

  private val rfiDocOid = new ObjectId("56fe4e6bd5d8ad3da60d5d38")

  private def docList(project: DynDoc, docIds: Seq[ObjectId], createdAfter: Long): DocumentList = {
    val docs: Seq[DynDoc] = docIds.map(id =>BWMongoDB3.document_master.find(Map("_id" -> id)).head)
    for (doc <- docs) {
      val isReady = if (project ? "documents") {
        project.documents[DocumentList].exists(d => d.document_id[ObjectId] == doc._id[ObjectId] &&
          d.timestamp[Long] > createdAfter)
      } else {
        false
      }
      doc.is_ready = isReady
    }
    docs.map(_.asDoc)
  }

  private def activityOidToPhaseAndProject(activityOid: ObjectId): (DynDoc, DynDoc) = {
    val phase: DynDoc = BWMongoDB3.phases.find(Map("activity_ids" -> activityOid)).head
    val phaseOid = phase._id[ObjectId]
    val project = BWMongoDB3.projects.find(Map("phase_ids" -> phaseOid)).head
    (phase, project)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val activityOid = new ObjectId(parameters("activity_id"))
      val activity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).head
      val actionOrder = Map("prerequisite" -> 1, "main" -> 2, "review" -> 3)
      val sortedActions: Seq[DynDoc] = activity.actions[DocumentList].
        sortWith((a, b) => actionOrder(a.`type`[String]) < actionOrder(b.`type`[String]))
      val (phase, project) = activityOidToPhaseAndProject(activityOid)
      for (action <- sortedActions) {
        action.review_ok = ""
        //action.description = s"""This is the placeholder description text for the action '${action.name[String]}'.
        //     |It will be replaced by the actual description when the system is in use.""".stripMargin.replaceAll("\n", " ")
        action.display_status = action.status[String]
        val isWaiting = action.status[String] == "waiting"
        val assigneeIsRequestor = action.assignee_person_id[ObjectId] == personOid
        val phaseManagerIsRequestor = phase.admin_person_id[ObjectId] == personOid
        if (isWaiting) {
          action.displayDetails = assigneeIsRequestor
          if (!assigneeIsRequestor)
            action.display_status = "waiting2"
        } else {
          action.displayDetails = false
        }
//        val p0 = if (project ? "timestamps") project.timestamps[Document].y.start[Long] else Long.MaxValue
//        action.inDocuments = docList(project, action.inbox[ObjectIdList], p0)
//        val t0 = if (action ? "timestamps") action.timestamps[Document].y.start[Long] else Long.MaxValue
//        action.outDocuments = docList(project, action.outbox[ObjectIdList] += rfiDocOid, t0)
//        val outDocs: Seq[DynDoc] = action.outDocuments[DocumentList]
//        action.is_ready = (action.`type`[String] == "review") || outDocs.forall(_.is_ready[Boolean])
        val isRelevant = assigneeIsRequestor | phaseManagerIsRequestor
        action.is_relevant = isRelevant
        if (isRelevant) {
          val p0 = if (project ? "timestamps") project.timestamps[Document].y.start[Long] else Long.MaxValue
          action.inDocuments = docList(project, action.inbox[ObjectIdList], p0)
          val t0 = if (action ? "timestamps") action.timestamps[Document].y.start[Long] else Long.MaxValue
//          val outDocs: ObjectIdList = if (action.name[String] == "DocZTest") {
//            val rfiDocs = new java.util.ArrayList[ObjectId]
//            rfiDocs.add(new ObjectId("56fe4e6bd5d8ad3da60d5d38")); rfiDocs.add(new ObjectId("56fe4e6bd5d8ad3da60d5d39"))
//            action.outbox[ObjectIdList] ++ rfiDocs
//          } else {
//            action.outbox[ObjectIdList]
//          }
          val outDocs = docList(project, action.outbox[ObjectIdList] += rfiDocOid, t0)
          action.outDocuments = outDocs
          action.is_ready = (action.`type`[String] == "review") ||
            outDocs.forall(doc => doc.is_ready[Boolean] || doc._id[ObjectId] == rfiDocOid)
        }
        action.remove("inbox")
        action.remove("outbox")
      }
      writer.print(sortedActions.map(activity => bson2json(activity.asDoc)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

}