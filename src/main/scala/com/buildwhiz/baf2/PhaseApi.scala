package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.BWLogger
import javax.servlet.http.HttpServletRequest
import org.bson.Document
import org.bson.types.ObjectId

object PhaseApi {

  def phasesByIds(phaseOids: Seq[ObjectId]): Seq[DynDoc] =
    BWMongoDB3.phases.find(Map("_id" -> Map($in -> phaseOids)))

  def phaseById(phaseOid: ObjectId): DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head

  def exists(phaseOid: ObjectId): Boolean = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).nonEmpty

  def allProcessOids(phase: DynDoc): Seq[ObjectId] = phase.process_ids[Many[ObjectId]]

  def allProcesses(phase: DynDoc): Seq[DynDoc] = {
    val processOids = allProcessOids(phase)
    ProcessApi.processesByIds(processOids)
  }

  def allProcesses(phaseOid: ObjectId): Seq[DynDoc] = allProcesses(phaseById(phaseOid))

  def allActivities(phase: DynDoc): Seq[DynDoc] = {
    val activityOids: Seq[ObjectId] = allProcesses(phase).flatMap(_.activity_ids[Many[ObjectId]])
    ActivityApi.activitiesByIds(activityOids)
  }

  def allActivities(phaseOid: ObjectId): Seq[DynDoc] = allActivities(phaseById(phaseOid))

  def isActive(phase: DynDoc): Boolean = allProcesses(phase).exists(process => ProcessApi.isActive(process))

  def phaseLevelUsers(phase: DynDoc): Seq[ObjectId] = {
    if (phase.has("assigned_roles")) {
      (phase.admin_person_id[ObjectId] +:
          phase.assigned_roles[Many[Document]].map(_.person_id[ObjectId])).distinct
    } else {
      Seq(phase.admin_person_id[ObjectId])
    }
  }

  def delete(phase: DynDoc, request: HttpServletRequest): Unit = {
    val phaseOid = phase._id[ObjectId]
    if (isActive(phase))
      throw new IllegalArgumentException(s"Phase '${phase.name[String]}' is still active")
    val phaseDeleteResult = BWMongoDB3.phases.deleteOne(Map("_id" -> phaseOid))
    if (phaseDeleteResult.getDeletedCount == 0)
      throw new IllegalArgumentException(s"MongoDB error: $phaseDeleteResult")
    val projectUpdateResult = BWMongoDB3.projects.updateOne(Map("phase_ids" -> phaseOid),
        Map("$pull" -> Map("phase_ids" -> phaseOid)))
    allProcesses(phase).foreach(process => ProcessApi.delete(process, request))
    val message = s"Deleted phase '${phase.name[String]}' (${phase._id[ObjectId]}). " +
      s"Also updated ${projectUpdateResult.getModifiedCount} project records"
    BWLogger.audit(getClass.getName, request.getMethod, message, request)
  }

  def parentProject(phaseOid: ObjectId): DynDoc = {
    BWMongoDB3.projects.find(Map("phase_ids" -> phaseOid)).head
  }

  def hasRole(personOid: ObjectId, phase: DynDoc): Boolean = {
    isAdmin(personOid, phase) || phase.assigned_roles[Many[Document]].exists(_.person_id[ObjectId] == personOid) ||
      allProcesses(phase).exists(process => ProcessApi.hasRole(personOid, process))
  }

  def isAdmin(personOid: ObjectId, phase: DynDoc): Boolean =
    phase.admin_person_id[ObjectId] == personOid

  def isManager(personOid: ObjectId, phase: DynDoc): Boolean = phase.assigned_roles[Many[Document]].
    exists(ar => ar.person_id[ObjectId] == personOid &&
      ar.role_name[String].matches("(?i)(?:project-|phase-)?manager"))

  def canManage(personOid: ObjectId, phase: DynDoc): Boolean =
      isManager(personOid, phase) || isAdmin(personOid, phase) ||
      ProjectApi.canManage(personOid, parentProject(phase._id[ObjectId]))

  def phasesByUser(personOid: ObjectId, parentProject: DynDoc): Seq[DynDoc] = {
    val phaseOids: Seq[ObjectId] = parentProject.phase_ids[Many[ObjectId]]
    val phases: Seq[DynDoc] = BWMongoDB3.phases.find(Map("_id" -> phaseOids))
    phases.filter(phase => hasRole(personOid, phase))
  }

  def hasZombies(phase: DynDoc): Boolean = allProcesses(phase).exists(ProcessApi.isZombie)

  def displayStatus(phase: DynDoc): String = {
    (isActive(phase), hasZombies(phase)) match {
      case (false, false) => "dormant"
      case (false, true) => "has-zombie"
      case (true, false) => "active"
      case (true, true) => "active+zombie"
    }
  }

  def validateNewName(newPhaseName: String, projectOid: ObjectId): Boolean = {
    val phaseNameLength = newPhaseName.length
    if (newPhaseName.trim.length != phaseNameLength)
      throw new IllegalArgumentException(s"Bad phase name (has blank padding): '$newPhaseName'")
    if (phaseNameLength > 150 || phaseNameLength < 5)
      throw new IllegalArgumentException(s"Bad phase name length: $phaseNameLength (must be 5-150)")
    val phaseOids: Seq[ObjectId] = ProjectApi.allPhaseOids(ProjectApi.projectById(projectOid))
    val count = BWMongoDB3.phases.count(Map("name" -> newPhaseName, "_id" -> Map($in -> phaseOids)))
    if (count > 0)
      throw new IllegalArgumentException(s"Phase named '$newPhaseName' already exists")
    true
  }

  def managers(phase: DynDoc): Seq[ObjectId] = {
    val project = parentProject(phase._id[ObjectId])
    val projectManagers = ProjectApi.managers(project)
    val phaseManagers = phase.assigned_roles[Many[Document]].
    filter(_.role_name[String].matches(".*(?i)manager")).map(_.person_id[ObjectId])
    projectManagers ++ phaseManagers
  }

}