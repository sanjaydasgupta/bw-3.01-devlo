package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.HttpUtils
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConversions._
import scala.collection.mutable

class OwnedActionsAll extends HttpServlet with HttpUtils {

  private val rfiRequestOid = new ObjectId("56fe4e6bd5d8ad3da60d5d38")
  private val rfiResponseOid = new ObjectId("56fe4e6bd5d8ad3da60d5d39")

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

  private def copyParentReferences(action: DynDoc, project: DynDoc, phase: DynDoc, activity: DynDoc): Unit = {
    action.project_name = project.name[String]
    action.project_id = project._id[ObjectId]
    action.phase_name = phase.name[String]
    action.phase_id = phase._id[ObjectId]
    action.activity_name = activity.name[String]
    action.activity_id = activity._id[ObjectId]
    action.activity_description = activity.description[String]
    action.group_name = s"${project.name[String]}/${phase.name[String]}/${action.bpmn_name[String]}"
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val projectOids: ObjectIdList = BWMongoDB3.persons.find(Map("_id" -> personOid)).head.y.project_ids[ObjectIdList]
      val projects: Seq[DynDoc] = BWMongoDB3.projects.find(Map("_id" -> Map("$in" -> projectOids))).toSeq
      //val actionOrder = Map("prerequisite" -> 1, "main" -> 2, "review" -> 3)
      val allActions = mutable.Buffer.empty[DynDoc]
      for (project <- projects) {
        val phaseOids = project.phase_ids[ObjectIdList]
        val phases: Seq[DynDoc] = BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> phaseOids))).toSeq
        for (phase <- phases) {
          val activityOids = phase.activity_ids[ObjectIdList]
          val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityOids))).toSeq
          for (activity <- activities) {
            val actions: Seq[DynDoc] = activity.actions[DocumentList]
            val relevantActions = actions.filter(action => action.assignee_person_id[ObjectId] == personOid ||
              phase.admin_person_id[ObjectId] == personOid)
            for (action <- relevantActions) {
              copyParentReferences(action, project, phase, activity)
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
                val p0 = if (project ? "timestamps") project.timestamps[Document].y.start[Long] else Long.MaxValue
                action.inDocuments = docList(project, action.inbox[ObjectIdList], p0)
                val t0 = if (action ? "timestamps") action.timestamps[Document].y.start[Long] else Long.MaxValue
                val outDocumentsOids: Seq[ObjectId] =
                  if (assigneeIsUser) {
                    rfiRequestOid +: action.outbox[ObjectIdList]
                  } else if (phaseManagerIsUser && action.inbox[ObjectIdList].contains(rfiRequestOid)) {
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
            allActions ++= relevantActions
          }
        }
      }
      writer.print(allActions.map(activity => bson2json(activity.asDoc)).mkString("[", ", ", "]"))
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