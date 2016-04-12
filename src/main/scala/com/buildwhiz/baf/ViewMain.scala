package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3, Utils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.language.implicitConversions

class ViewMain extends HttpServlet with Utils {

  private def addEmbeddedObjects(personId: ObjectId)(proj: Document): Document = {

    def docId2Document(project: DynDoc, docIds: ObjectIdList, createdAfter: Long): DocumentList = {
      val docs: Seq[DynDoc] = docIds.map(id =>BWMongoDB3.document_master.find(Map("_id" -> id)).head)
      for (doc <- docs) {
        val isReady = if (project ? "documents") {
        //val isReady = if (action ? "uploaded_documents") {
          project.documents[DocumentList].exists(d => d.document_id[ObjectId] == doc._id[ObjectId] &&
            //d.timestamp[Long] > createdAfter)
            //action.uploaded_documents[DocumentList].exists(d => d.document_id[ObjectId] == doc._id[ObjectId] &&
            d.timestamp[Long] > createdAfter)
        } else
          false
        doc.is_ready = isReady
      }
      docs.map(_.asDoc)
    }

    val project: DynDoc = proj
    project.is_manager = project.admin_person_id[ObjectId] == personId
    project.displayDetails = project.status[String] matches "waiting|defined"
    val phaseIds: ObjectIdList = project.phase_ids
    val allPhases: Seq[DynDoc] = BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> phaseIds))).toSeq
    val relevantPhases = mutable.Buffer.empty[DynDoc]
    for (phase <- allPhases) {
      val isWaiting = phase.status[String] == "waiting"
      phase.displayDetails = isWaiting
      if (isWaiting)
        project.displayDetails = true
      val activityIds: ObjectIdList = phase.activity_ids
      val relevantActivities = new java.util.ArrayList[Document]
      val activities: Seq[DynDoc] = BWMongoDB3.activities.
        find(Map("_id" -> Map("$in" -> activityIds))).toSeq
      for (activity <- activities) {
        activity.displayDetails = false
        val actionOrder = Map("prerequisite" -> 1, "main" -> 2, "review" -> 3)
        val sortedActions: Seq[DynDoc] = activity.actions[DocumentList].
          sortWith((a, b) => actionOrder(a.`type`[String]) < actionOrder(b.`type`[String]))
        val filteredActions = sortedActions/*.
          filter(_("assignee_person_id") == personId)*/
        filteredActions.foreach(action => {
          action.review_ok = "OK"
          val isWaiting = action.status[String] == "waiting"
          if (isWaiting) {
            action.displayDetails = action.assignee_person_id[ObjectId] == personId
            activity.displayDetails = true
            phase.displayDetails = true
            project.displayDetails = true
          } else {
            action.displayDetails = false
          }
          val p0 = if (project ? "timestamps") project.timestamps[Document].y.start[Long] else Long.MaxValue
          action.inDocuments = docId2Document(project, action.inbox[ObjectIdList], p0)
          //action.inDocuments = docId2Document(action, action.inbox[ObjectIdList], p0)
          val t0 = if (action ? "timestamps") action.timestamps[Document].y.start[Long] else Long.MaxValue
          action.outDocuments = docId2Document(project, action.outbox[ObjectIdList], t0)
          //action.outDocuments = docId2Document(action, action.outbox[ObjectIdList], t0)
          val outDocs: Seq[DynDoc] = action.outDocuments[DocumentList]
          action.is_ready = outDocs.forall(_.is_ready[Boolean])
        })
        activity.actions = filteredActions.map(_.asDoc)
        relevantActivities += activity.asDoc
//        if (relevantPhases.count(_._id[ObjectId] == phase._id[ObjectId]) == 0) {
//          relevantPhases += phase
//        }
      }
      phase.is_manager = phase.admin_person_id[ObjectId] == personId
      relevantPhases += phase
      phase.activities = relevantActivities
    }
    val phasesList: DocumentList = relevantPhases.map(_.asDoc)
    project.phases = phasesList
    project.asDoc
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
      val projectOids: Seq[ObjectId] = person.project_ids[ObjectIdList]
      val projects: Seq[Document] = BWMongoDB3.projects.find(Map("_id" -> Map("$in" -> projectOids))).toSeq.
        map(addEmbeddedObjects(personOid))
      writer.print(projects.map(bson2json).mkString("[", ", ", "]"))
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