package com.buildwhiz.baf2

import java.net.URI

import com.amazonaws.services.s3.model.S3ObjectSummary
import com.buildwhiz.infra.{AmazonS3, BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

object ProjectApi {

  def projectById(projectOid: ObjectId): DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head

  def exists(projectOid: ObjectId): Boolean = BWMongoDB3.projects.find(Map("_id" -> projectOid)).nonEmpty

  def allPhaseOids(parentProject: DynDoc): Seq[ObjectId] = parentProject.phase_ids[Many[ObjectId]]

  def allPhases(parentProject: DynDoc): Seq[DynDoc] =
      BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> allPhaseOids(parentProject))))

  def allProcesses(parentProject: DynDoc): Seq[DynDoc] =
      BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> allPhaseOids(parentProject)))).
      flatMap(phase => PhaseApi.allProcesses(phase))

  def allProcesses(parentProjectOid: ObjectId): Seq[DynDoc] = allProcesses(projectById(parentProjectOid))

  def phasesByUser(userOid: ObjectId, parentProject: DynDoc): Seq[DynDoc] =
    allPhases(parentProject).filter(phase => PhaseApi.hasRoleInPhase(userOid, phase))

  def allActivities(project: DynDoc): Seq[DynDoc] = allProcesses(project).
      flatMap(phase => ProcessApi.allActivities(phase))

  def allActivities(pOid: ObjectId): Seq[DynDoc] = allActivities(projectById(pOid))

  //def allActions(project: DynDoc): Seq[DynDoc] = allActivities(project).flatMap(Activity.allActions)

  def projectLevelUsers(project: DynDoc): Seq[ObjectId] = {
    if (project.has("assigned_roles")) {
      (project.admin_person_id[ObjectId] +:
          project.assigned_roles[Many[Document]].map(_.person_id[ObjectId])).distinct
    } else {
      Seq(project.admin_person_id[ObjectId])
    }
  }

  def allProjectUsers(project: DynDoc): Seq[ObjectId] = {
    val phaseUsers = Nil //allPhases(project).flatMap(Phase.allPhaseUsers)
    (projectLevelUsers(project) ++ phaseUsers).distinct
  }

  def renewUserAssociations(request: HttpServletRequest, projectOidOption: Option[ObjectId] = None): Unit = {

    projectOidOption match {
      case None => BWMongoDB3.projects.find().foreach(proj => renewUserAssociations(request, Some(proj._id[ObjectId])))

      case Some(projectOid) =>
        val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
        val userOids: Seq[ObjectId] = allProjectUsers(project)

        val updateResult = BWMongoDB3.persons.updateMany(Map("_id" -> Map("$in" -> userOids)),
            Map("$addToSet" -> Map("project_ids" -> projectOid)))
        val updateResult2 = BWMongoDB3.persons.updateMany(Map("_id" -> Map("$nin" -> userOids)),
          Map("$pull" -> Map("project_ids" -> projectOid)))

        if (updateResult.getMatchedCount + updateResult2.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB error: $updateResult, $updateResult2")

        val userNames = userOids.map(userOid => {
          val user: DynDoc = BWMongoDB3.persons.find(Map("_id" -> userOid)).head
          s"${user.first_name[String]} ${user.last_name[String]}"
        })
        val message = s"""Project '${project.name[String]}' linked to users ${userNames.mkString(",")}"""
        BWLogger.audit(getClass.getName, "renewUserAssociation", message, request)
    }

  }

/*
  def canEnd(project: DynDoc): Boolean = {
    val projectAlreadyEnded = project.status[String] == "ended"
    val phases: Seq[DynDoc] = BWMongoDB3.processes.find(Map("_id" -> Map("$in" -> project.process_ids[Many[ObjectId]])))
    !phases.exists(_.status[String] == "running") && !projectAlreadyEnded
  }
*/

  def isActive(project: DynDoc): Boolean = allPhases(project).exists(phase => ProcessApi.isActive(phase))

  def delete(project: DynDoc, request: HttpServletRequest): Unit = {
    val projectOid = project._id[ObjectId]
    if (isActive(project))
      throw new IllegalArgumentException(s"Project '$projectOid' is still active")
    val projectDeleteResult = BWMongoDB3.projects.deleteOne(Map("_id" -> projectOid))
    if (projectDeleteResult.getDeletedCount == 0)
      throw new IllegalArgumentException(s"MongoDB error: $projectDeleteResult")
    allPhases(project).foreach(phase => PhaseApi.delete(phase, request))
    val message = s"Deleted project '${project.name[String]}' (${project._id[ObjectId]})"
    BWLogger.audit(getClass.getName, request.getMethod, message, request)
  }

  def hasRoleInProject(personOid: ObjectId, project: DynDoc): Boolean =
      project.admin_person_id[ObjectId] == personOid ||
      project.assigned_roles[Many[Document]].exists(_.person_id[ObjectId] == personOid)

  def isProjectManager(personOid: ObjectId, project: DynDoc): Boolean =
      project.admin_person_id[ObjectId] == personOid || project.assigned_roles[Many[Document]].
      exists(proj => proj.person_id[ObjectId] == personOid && proj.role_name[String] == "Project-Manager")

  def projectsByUser(personOid: ObjectId): Seq[DynDoc] = {
    val projects: Seq[DynDoc] = BWMongoDB3.projects.find()
    projects.filter(project => hasRoleInProject(personOid, project))
  }

}