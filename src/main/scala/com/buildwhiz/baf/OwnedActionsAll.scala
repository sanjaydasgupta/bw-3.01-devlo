package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._
import scala.collection.mutable

class OwnedActionsAll extends HttpServlet with HttpUtils {

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
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val projectOids: Many[ObjectId] = BWMongoDB3.persons.find(Map("_id" -> personOid)).head.project_ids[Many[ObjectId]]
      val projects: Seq[DynDoc] = BWMongoDB3.projects.find(Map("_id" -> Map("$in" -> projectOids)))
      //val actionOrder = Map("prerequisite" -> 1, "main" -> 2, "review" -> 3)
      val allActions = mutable.Buffer.empty[DynDoc]
      for (project <- projects) {
        val phaseOids = project.phase_ids[Many[ObjectId]]
        val phases: Seq[DynDoc] = BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> phaseOids)))
        for (phase <- phases) {
          val activityOids = phase.activity_ids[Many[ObjectId]]
          val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityOids)))
          for (activity <- activities) {
            val actions: Seq[DynDoc] = activity.actions[Many[Document]]
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
              action.phase_manager_is_user = phaseManagerIsUser
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
                val p0 = if (project has "timestamps") project.timestamps[Document].y.start[Long] else Long.MaxValue
                action.inDocuments = docList(project, action.inbox[Many[ObjectId]], p0)
                val t0 = if (action has "timestamps") action.timestamps[Document].y.start[Long] else Long.MaxValue
                val outDocumentsOids: Seq[ObjectId] =
                  if (assigneeIsUser) {
                    rfiRequestOid +: action.outbox[Many[ObjectId]]
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
            allActions ++= relevantActions
          }
        }
      }
      response.getWriter.print(allActions.map(activity => bson2json(activity.asDoc)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}