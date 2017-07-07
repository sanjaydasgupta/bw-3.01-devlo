package com.buildwhiz.api

import java.net.URI
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.BWLogger
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class Phase extends HttpServlet with RestUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doPost", s"ENTRY", request)
    try {
      //val phasesCollection = BWMongoDB3.phases
      val postData = getStreamData(request)
      val phaseDocument = Document.parse(postData)
      if (!phaseDocument.containsKey("parent_project_id"))
        throw new IllegalArgumentException("No 'parent_project_id' found")
      if (!phaseDocument.get("parent_project_id").isInstanceOf[ObjectId])
        throw new IllegalArgumentException("Type of 'parent_project_id' not ObjectId")
      phaseDocument.asScala("status") = "defined" // Initial status on creation
      phaseDocument.asScala("timestamps") = new Document("created", System.currentTimeMillis)
      //val parentProjectSelector = Map("_id", phaseDocument.get("parent_project_id"))
      BWMongoDB3.phases.insertOne(phaseDocument)
      //val newPhaseId = phaseDocument.getObjectId("_id")

      BWMongoDB3.projects.updateOne(Map("_id" -> phaseDocument.get("parent_project_id")),
        Map("$push" -> Map("phase_ids" -> phaseDocument.getObjectId("_id"))))
      response.setContentType("text/plain")
      response.getWriter.print(s"${request.getRequestURI}/${phaseDocument.getObjectId("_id")}")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.audit(getClass.getName, "doPost", s"""Added Phase '${phaseDocument.get("name")}'""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  override def doPut(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestPut(request, response, "Phase", "phases")
  }

  private def projectParticipantOids(theProject: DynDoc): Seq[ObjectId] = {
    val phaseOids = theProject.phase_ids[Many[ObjectId]]
    val phases: Seq[DynDoc] = BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> phaseOids)))
    val phaseAdminPersonOids: Seq[ObjectId] = phases.map(_.admin_person_id[ObjectId])
    val activityOids = phases.flatMap(_.activity_ids[Many[ObjectId]])
    val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityOids)))
    val actions: Seq[DynDoc] = activities.flatMap(_.actions[Many[Document]])
    val actionAssigneeOids = actions.map(_.assignee_person_id[ObjectId])
    (theProject.admin_person_id[ObjectId] +: (phaseAdminPersonOids ++ actionAssigneeOids)).distinct
  }

  override def doDelete(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doDelete", s"ENTRY", request)
    try {
      val uriParts = new URI(request.getRequestURI.replace("[", "%5B").replace("]", "%5D")).getPath.split("/").toSeq.reverse
      val phaseOid = uriParts match {
        case idString +: "Phase" +: _ => new ObjectId(idString)
        case _ => throw new IllegalArgumentException("Id not found")
      }
      val thePhase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val theProject: DynDoc = BWMongoDB3.projects.find(Map("phase_ids" -> phaseOid)).head
      val preDeleteParticipantOids = projectParticipantOids(theProject)
      val activityOids: Seq[ObjectId] = thePhase.activity_ids[Many[ObjectId]]
      BWMongoDB3.activities.deleteMany(Map("_id" -> Map("$in" -> activityOids)))
      BWMongoDB3.phases.deleteOne(Map("_id" -> phaseOid))
      BWMongoDB3.projects.updateMany(new Document(/* optimization possible */),
        Map("$pull" -> Map("phase_ids" -> phaseOid)))
      val postDeleteParticipantOids = projectParticipantOids(theProject)
      val affectedPersonOids = preDeleteParticipantOids.diff(postDeleteParticipantOids)
      BWMongoDB3.persons.updateMany(Map("_id" -> Map("$in" -> affectedPersonOids)),
        Map("$pull" -> Map("project_ids" -> theProject._id[ObjectId])))
      val phaseNameAndId = s"""${thePhase.name[String]} (${thePhase._id[ObjectId]})"""
      BWLogger.audit(getClass.getName, "doDelete", s"""Deleted Phase '$phaseNameAndId'""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doDelete", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestGet(request, response, "Phase", "phases")
  }

}