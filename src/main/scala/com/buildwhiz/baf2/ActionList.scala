package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class ActionList extends HttpServlet with HttpUtils {

  private def docList(project: DynDoc, docIds: Seq[ObjectId], startTime: Long): Many[Document] = {
    val docs: Seq[DynDoc] = docIds.map(id =>BWMongoDB3.document_master.find(Map("_id" -> id)).head)
    for (doc <- docs) {
      val isReady = if (project has "documents") {
        project.documents[Many[Document]].exists(d => d.document_id[ObjectId] == doc._id[ObjectId] &&
          d.timestamp[Long] > startTime)
      } else {
        false
      }
      doc.is_ready = isReady
    }
    docs.map(_.asDoc)
  }.asJava

  private def getViewObjects(action: DynDoc): Document = {
    val activityOid = action.activity_id[ObjectId]
    val viewAction: DynDoc = new Document()
    viewAction.name = action.name[String]
    viewAction.completion_message = if (action has "completion_message") action.completion_message[String] else ""
    viewAction.reviewOk = if (action has "review_ok") action.review_ok[Boolean] else false
    viewAction.`type` = action.`type`[String]
    viewAction.status = action.status[String]

    val theProcess = ActivityApi.parentProcess(activityOid)
    viewAction.process_name = theProcess.name[String]
    viewAction.process_id = theProcess._id[ObjectId]

    val thePhase = ProcessApi.parentPhase(theProcess._id[ObjectId])
    viewAction.phase_name = thePhase.name[String]
    viewAction.phase_id = thePhase._id[ObjectId]

    val theProject = PhaseApi.parentProject(thePhase._id[ObjectId])
    viewAction.project_name = theProject.name[String]
    viewAction.project_id = theProject._id[ObjectId]

    val theActivity = ActivityApi.activityById(activityOid)
    viewAction.activity_name = theActivity.name[String]
    viewAction.activity_id = theActivity._id[ObjectId]
    viewAction.activity_description = theActivity.description[String]

    viewAction.group_name = s"${theProject.name[String]}/${theProcess.name[String]}/${action.bpmn_name[String]}"
    val p0 = if (theProject has "timestamps") {
      val timestamps: DynDoc = theProject.timestamps[Document]
      if (timestamps.has("start")) timestamps.start[Long] else Long.MaxValue
    } else {
      Long.MaxValue
    }
    viewAction.in_documents = docList(theProject, action.inbox[Many[ObjectId]], p0)
    val t0 = if (action has "timestamps") action.timestamps[Document].y.start[Long] else Long.MaxValue
    val outDocumentsOids: Seq[ObjectId] = submittalOid +: action.outbox[Many[ObjectId]]
    val outDocs = docList(theProject, outDocumentsOids, t0)
    viewAction.out_documents = outDocs
    viewAction.is_ready = (action.`type`[String] == "review") ||
      outDocs.forall(doc => doc.is_ready[Boolean] || doc._id[ObjectId] == rfiRequestOid)
    viewAction.asDoc
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      val filterKey = parameters("filter_key")
      //val actionOrder = Map("prerequisite" -> 1, "main" -> 2, "review" -> 3)
      val allActions = ActivityApi.actionsByUser(userOid)
      val filteredActions = allActions.filter(action => filterKey match {
        case "active" => action.status[String] == "waiting"
        case "all" => true
        case _ => true // placeholder, to be changed later
      }).map(getViewObjects)
      response.getWriter.print(filteredActions.map(activity => bson2json(activity.asDoc)).mkString("[", ", ", "]"))
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