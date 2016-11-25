package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.HttpUtils
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._
import scala.collection.mutable

class OwnedActionsSummary extends HttpServlet with HttpUtils {

  private def docList(project: DynDoc, docIds: Seq[ObjectId], startTime: Long): DocumentList = {
    val docs: Seq[DynDoc] = docIds.map(id =>BWMongoDB3.document_master.find(Map("_id" -> id)).asScala.head)
    for (doc <- docs) {
      val isReady = if (project has "documents") {
        project.documents[DocumentList].exists(d => d.document_id[ObjectId] == doc._id[ObjectId] &&
          d.timestamp[Long] > startTime)
      } else {
        false
      }
      doc.is_ready = isReady
    }
    docs.map(_.asDoc)
  }.asJava

  private def getViewObjects(request: HttpServletRequest, action: DynDoc, project: DynDoc, phase: DynDoc, activity: DynDoc): Document = {
    val viewAction = new Document().y
    viewAction.name = action.name[String]
    viewAction.reviewOk = if (action has "review_ok") action.review_ok[Boolean] else false
    viewAction.`type` = action.`type`[String]
    viewAction.status = action.status[String]
    viewAction.project_name = project.name[String]
    viewAction.project_id = project._id[ObjectId]
    viewAction.phase_name = phase.name[String]
    viewAction.phase_id = phase._id[ObjectId]
    viewAction.activity_name = activity.name[String]
    viewAction.activity_id = activity._id[ObjectId]
    viewAction.activity_description = activity.description[String]
    viewAction.group_name = s"${project.name[String]}/${phase.name[String]}/${action.bpmn_name[String]}"
    val p0 = if (project has "timestamps") project.timestamps[Document].y.start[Long] else Long.MaxValue
    viewAction.in_documents = docList(project, action.inbox[ObjectIdList].asScala, p0)
    val t0 = if (action has "timestamps") action.timestamps[Document].y.start[Long] else Long.MaxValue
    val outDocumentsOids: Seq[ObjectId] = submittalOid +: action.outbox[ObjectIdList].asScala
    val outDocs = docList(project, outDocumentsOids, t0)
    viewAction.out_documents = outDocs
    viewAction.is_ready = (action.`type`[String] == "review") ||
      outDocs.forall(doc => doc.is_ready[Boolean] || doc._id[ObjectId] == rfiRequestOid)
    //BWLogger.log(getClass.getName, "getViewObjects()", bson2json(viewAction.asDoc), request)
    viewAction.asDoc
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val filterKey = parameters("filter_key")
      val projectOids: ObjectIdList = BWMongoDB3.persons.find(Map("_id" -> personOid)).asScala.head.y.project_ids[ObjectIdList]
      val projects: Seq[DynDoc] = BWMongoDB3.projects.find(Map("_id" -> Map("$in" -> projectOids))).asScala.toSeq
      //val actionOrder = Map("prerequisite" -> 1, "main" -> 2, "review" -> 3)
      val allActions = mutable.Buffer.empty[DynDoc]
      for (project <- projects) {
        val phaseOids = project.phase_ids[ObjectIdList]
        val phases: Seq[DynDoc] = BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> phaseOids))).asScala.toSeq
        for (phase <- phases) {
          val activityOids = phase.activity_ids[ObjectIdList]
          val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityOids))).asScala.toSeq
          for (activity <- activities) {
            val actions: Seq[DynDoc] = activity.actions[DocumentList]
            val relevantActions = actions.filter(action => action.assignee_person_id[ObjectId] == personOid)
            val filteredActions = relevantActions.filter(action => filterKey match {
              case "active" => action.status[String] == "waiting"
              case "all" => true
              case _ => true // placeholder, to be changed later
            })
            allActions ++= filteredActions.map(a => getViewObjects(request, a, project, phase, activity))
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