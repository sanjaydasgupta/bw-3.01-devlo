package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.HttpUtils
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class PreloadedDocumentsList extends HttpServlet with HttpUtils {

  private def docList(project: DynDoc, docIds: Seq[ObjectId], createdAfter: Long): DocumentList = {
    val docs: Seq[DynDoc] = docIds.map(id =>BWMongoDB3.document_master.find(Map("_id" -> id)).asScala.head)
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
  }.asJava

  private def activityOidToPhaseAndProject(activityOid: ObjectId): (DynDoc, DynDoc) = {
    val phase: DynDoc = BWMongoDB3.phases.find(Map("activity_ids" -> activityOid)).asScala.head
    val phaseOid = phase._id[ObjectId]
    val project = BWMongoDB3.projects.find(Map("phase_ids" -> phaseOid)).asScala.head
    (phase, project)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).asScala.head
      val uploadedDocuments: Seq[DynDoc] = if (project ? "documents") project.documents[DocumentList] else Nil
      val uploadedDocumentsById: Map[ObjectId, Seq[DynDoc]] = uploadedDocuments.groupBy(_.document_id[ObjectId])
      val documentMasters: Seq[DynDoc] = BWMongoDB3.document_master.find().asScala.toSeq
      val preloadedDocuments = documentMasters.filter(_ ? "preload")
      preloadedDocuments.foreach(d => d.asDoc.put("available", uploadedDocumentsById.contains(d._id[ObjectId])))
      writer.print(preloadedDocuments.map(d => bson2json(d.asDoc)).mkString("[", ", ", "]"))
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