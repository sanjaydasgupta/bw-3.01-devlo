package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.HttpServletRequest
import org.bson.Document
import org.bson.types.ObjectId

object ProjectApi extends HttpUtils {

  def projectById(projectOid: ObjectId): DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head

  def exists(projectOid: ObjectId): Boolean = BWMongoDB3.projects.find(Map("_id" -> projectOid)).nonEmpty

  def fetch(oid: Option[ObjectId] = None, name: Option[String] = None): Seq[DynDoc] =
    (oid, name) match {
      case (Some(theOid), _) => BWMongoDB3.projects.find(Map("_id" -> theOid))
      case (None, Some(theName)) => BWMongoDB3.projects.find(Map("name" -> theName))
      case _ => BWMongoDB3.projects.find()
    }

  def allPhaseOids(parentProject: DynDoc): Seq[ObjectId] = parentProject.phase_ids[Many[ObjectId]]

  def allPhases(parentProject: DynDoc): Seq[DynDoc] =
      BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> allPhaseOids(parentProject))))

  def allProcesses(parentProject: DynDoc): Seq[DynDoc] =
      allPhases(parentProject).flatMap(phase => PhaseApi.allProcesses(phase))

  def allProcesses(parentProjectOid: ObjectId): Seq[DynDoc] = allProcesses(projectById(parentProjectOid))

  def phasesByUser(userOid: ObjectId, parentProject: DynDoc): Seq[DynDoc] =
    allPhases(parentProject).filter(phase => PhaseApi.hasRole(userOid, phase))

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

  def isActive(project: DynDoc): Boolean = allPhases(project).exists(phase => PhaseApi.isActive(phase))

  def delete(project: DynDoc, request: HttpServletRequest): Unit = {
    if (project.status[String] != "ended")
      throw new IllegalArgumentException(s"Project '${project.name[String]}' is still active")
    val projectOid = project._id[ObjectId]
    if (isActive(project))
      throw new IllegalArgumentException(s"Project '${project.name[String]}' is still active")
    val projectDeleteResult = BWMongoDB3.projects.deleteOne(Map("_id" -> projectOid))
    if (projectDeleteResult.getDeletedCount == 0)
      throw new IllegalArgumentException(s"MongoDB error: $projectDeleteResult")
    allPhases(project).foreach(phase => PhaseApi.delete(phase, request))
    val message = s"Deleted project '${project.name[String]}' (${project._id[ObjectId]})"
    BWLogger.audit(getClass.getName, request.getMethod, message, request)
  }

  def hasRole(personOid: ObjectId, project: DynDoc): Boolean = {
    isAdmin(personOid, project) || project.assigned_roles[Many[Document]].exists(_.person_id[ObjectId] == personOid) ||
    allPhases(project).exists(phase => PhaseApi.hasRole(personOid, phase))
  }

  def isAdmin(personOid: ObjectId, project: DynDoc): Boolean =
      project.admin_person_id[ObjectId] == personOid

  def isManager(personOid: ObjectId, project: DynDoc): Boolean = project.assigned_roles[Many[Document]].
      exists(ar => ar.person_id[ObjectId] == personOid &&
      ar.role_name[String].matches("(?i)(?:project-)?manager"))

  def canManage(personOid: ObjectId, project: DynDoc): Boolean =
      isManager(personOid, project) || isAdmin(personOid, project)

  def projectsByUser(personOid: ObjectId): Seq[DynDoc] = {
    val projects: Seq[DynDoc] = BWMongoDB3.projects.find()
    projects.filter(project => hasRole(personOid, project))
  }

  def documentTags(project: DynDoc): Seq[DynDoc] = {
    if (project.has("document_tags")) {
      project.document_tags[Many[Document]]
    } else {
      Seq.empty[Document]
    }
  }

  def deleteDocumentTag(tagName: String, project: DynDoc, request: HttpServletRequest): Unit = {
    val tags = documentTags(project)
    if (!tags.exists(_.name[String] == tagName))
      throw new IllegalArgumentException(s"Unknown tag: '$tagName'")
    val user: DynDoc = getUser(request)
    val userOid = user._id[ObjectId]
    if (!ProjectApi.isAdmin(userOid, project))
      throw new IllegalArgumentException("Not permitted")
    val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> project._id[ObjectId]),
      Map("$pull" -> Map("document_tags" -> Map("name" -> tagName))))
    if (updateResult.getMatchedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    BWMongoDB3.document_master.updateOne(Map("project_id" -> project._id[ObjectId]),
      Map("$pull" -> Map("labels" -> tagName)))
  }

  def addDocumentTag(tagName: String, project: DynDoc, optLogic: Option[String], request: HttpServletRequest): Unit = {
    val tags = documentTags(project)
    if (tags.exists(_.name[String] == tagName))
      throw new IllegalArgumentException(s"Tag: '$tagName' already exists")
    val user: DynDoc = getUser(request)
    val userOid = user._id[ObjectId]
    if (!ProjectApi.isAdmin(userOid, project))
      throw new IllegalArgumentException("Not permitted")
    val tagRecord = optLogic match {
      case None => new Document("name", tagName)
      case Some(logic) => new Document("name", tagName).append("logic", logic)
    }
    val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> project._id[ObjectId]),
      Map("$addToSet" -> Map("document_tags" -> tagRecord)))
    if (updateResult.getMatchedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    // Remove tag from documents?
  }

  def documentGroupManageLabels(project: DynDoc, documentOids: Seq[ObjectId], tagNames: Seq[String],
       operation: String): Unit = {
    val allSystemTags = documentTags(project)
    val badTagNames = tagNames.filter(tagName => !allSystemTags.exists(_.name[String] == tagName))
    if (badTagNames.nonEmpty)
      throw new IllegalArgumentException(s"""Bad tag-names: ${badTagNames.mkString(", ")}""")

    val badDocumentOids = documentOids.filter(oid => !DocumentApi.exists(oid))
    if (badDocumentOids.nonEmpty)
      throw new IllegalArgumentException(s"""Bad document-ids: ${badDocumentOids.mkString(", ")}""")

    if (operation == "add") {
      for (docOid <- documentOids) {
        val updateResult = BWMongoDB3.document_master.updateOne(Map("_id" -> docOid),
          Map("$addToSet" -> Map("labels" -> Map("$each" -> tagNames))))
        if (updateResult.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      }
    } else if (operation == "remove") {
      for (docOid <- documentOids) {
        val updateResult = BWMongoDB3.document_master.updateOne(Map("_id" -> docOid),
          Map("$pullAll" -> Map("labels" -> tagNames)))
        if (updateResult.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      }
    } else
      throw new IllegalArgumentException(s"Bad operation: '$operation'")
  }

  def displayStatus(project: DynDoc): String = {
    if (isActive(project))
      "active"
    else
      "dormant"
  }

}