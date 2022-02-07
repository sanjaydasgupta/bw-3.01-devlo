package com.buildwhiz.api

import java.net.URI
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.BWLogger
import org.bson.Document
import org.bson.types.ObjectId

import scala.jdk.CollectionConverters._

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
      BWMongoDB3.processes.insertOne(phaseDocument)
      //val newPhaseId = phaseDocument.getObjectId("_id")

      BWMongoDB3.projects.updateOne(Map("_id" -> phaseDocument.get("parent_project_id")),
        Map("$push" -> Map("process_ids" -> phaseDocument.getObjectId("_id"))))
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

//  private def projectParticipantOids(theProject: DynDoc): Seq[ObjectId] = {
//    val phaseOids = theProject.phase_ids[Many[ObjectId]]
//    val phases: Seq[DynDoc] = BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> phaseOids)))
//    val phaseAdminPersonOids: Seq[ObjectId] = phases.map(_.admin_person_id[ObjectId])
//    val activityOids = phases.flatMap(_.activity_ids[Many[ObjectId]])
//    val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityOids)))
//    val actions: Seq[DynDoc] = activities.flatMap(_.actions[Many[Document]])
//    val actionAssigneeOids = actions.map(_.assignee_person_id[ObjectId])
//    (theProject.admin_person_id[ObjectId] +: (phaseAdminPersonOids ++ actionAssigneeOids)).distinct
//  }
//
  override def doDelete(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doDelete", s"ENTRY", request)
    try {
      val uriParts = new URI(request.getRequestURI.replace("[", "%5B").replace("]", "%5D")).getPath.split("/").toSeq.reverse
      val phaseOid = uriParts match {
        case idString +: "Phase" +: _ => new ObjectId(idString)
        case _ => throw new IllegalArgumentException("Id not found")
      }
      val thePhase: DynDoc = BWMongoDB3.processes.find(Map("_id" -> phaseOid)).head
      val theProject: DynDoc = BWMongoDB3.projects.find(Map("process_ids" -> phaseOid)).head
      //val preDeleteParticipantOids = projectParticipantOids(theProject)
      val activityOids: Seq[ObjectId] = thePhase.activity_ids[Many[ObjectId]]
      val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityOids)))
      val actionNamesByActivityOid: Seq[(ObjectId, Seq[String])] = activities.
        map(activity => (activity._id[ObjectId], activity.actions[Many[Document]].map(_.name[String])))
      for ((activityOid, actionNames) <- actionNamesByActivityOid) {
        BWMongoDB3.document_master.deleteMany(
          Map("activity_id" -> activityOid, "action_name" -> Map("$in" -> actionNames)))
      }
      BWMongoDB3.activities.deleteMany(Map("_id" -> Map("$in" -> activityOids)))
      BWMongoDB3.processes.deleteOne(Map("_id" -> phaseOid))
      BWMongoDB3.projects.updateMany(new Document(/* optimization possible */),
        Map("$pull" -> Map("process_ids" -> phaseOid)))
      //val postDeleteParticipantOids = projectParticipantOids(theProject)
      //val affectedPersonOids = preDeleteParticipantOids.diff(postDeleteParticipantOids)
      //BWMongoDB3.persons.updateMany(Map("_id" -> Map("$in" -> affectedPersonOids)),
      //  Map("$pull" -> Map("project_ids" -> theProject._id[ObjectId])))
      Project.renewUserAssociations(request, Some(theProject._id[ObjectId]))
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

object Phase {

  def allActivityOids(phase: DynDoc): Seq[ObjectId] = phase.activity_ids[Many[ObjectId]]
  def allActivities(phase: DynDoc): Seq[DynDoc] = allActivityOids(phase).
    map(activityOid => BWMongoDB3.activities.find(Map("_id" -> activityOid)).head)
  def allActions(phase: DynDoc): Seq[DynDoc] = allActivities(phase).flatMap(_.actions[Many[Document]])

  def phaseLevelUsers(phase: DynDoc): Seq[ObjectId] = {
    if (phase.has("assigned_roles")) {
      (phase.admin_person_id[ObjectId] +:
          phase.assigned_roles[Many[Document]].map(_.person_id[ObjectId])).distinct
    } else {
      Seq(phase.admin_person_id[ObjectId])
    }
  }

  def allPhaseUsers(phase: DynDoc): Seq[ObjectId] = {

    val actionUsers = {
      val actions = Phase.allActions(phase)
      actions.flatMap(Action.actionUsers)
    }

    (phaseLevelUsers(phase) ++ actionUsers).distinct
  }

  def hasRoleInPhase(personOid: ObjectId, phase: DynDoc): Boolean =
      phase.admin_person_id[ObjectId] == personOid ||
      phase.assigned_roles[Many[Document]].exists(_.person_id[ObjectId] == personOid)

  def isPhaseManager(personOid: ObjectId, phase: DynDoc): Boolean =
    phase.admin_person_id[ObjectId] == personOid || phase.assigned_roles[Many[Document]].
      exists(proj => proj.person_id[ObjectId] == personOid && proj.role_name[String] == "Project-Manager")

  def phasesByUser(personOid: ObjectId, parentProject: DynDoc): Seq[DynDoc] = {
    val phaseOids: Seq[ObjectId] = parentProject.phase_ids[Many[ObjectId]]
    val phases: Seq[DynDoc] = BWMongoDB3.phases.find(Map("_id" -> phaseOids))
    phases.filter(phase => hasRoleInPhase(personOid, phase))
  }

}