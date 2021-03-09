package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc, GoogleDrive}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.HttpServletRequest
import org.bson.Document
import org.bson.types.ObjectId

import scala.util.Either

object ProjectApi extends HttpUtils {

  def listProjects(): Seq[DynDoc] = {
    BWMongoDB3.projects.find()
  }

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

  def allPhases(parentProjectOid: ObjectId): Seq[DynDoc] =
      allPhases(projectById(parentProjectOid))

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
      isManager(personOid, project) || PersonApi.isBuildWhizAdmin(Left(personOid)) || isAdmin(personOid, project)

  def projectsByUser(personOid: ObjectId): Seq[DynDoc] = {
    val projects: Seq[DynDoc] = BWMongoDB3.projects.find()
    projects.filter(project => hasRole(personOid, project))
  }

  def projectsByQuery(query: Map[String, Any], optPersonOid: Option[ObjectId] = None): Seq[DynDoc] = {
    val projects: Seq[DynDoc] = BWMongoDB3.projects.find(query)
    optPersonOid match {
      case Some(personOid) => projects.filter(project => hasRole(personOid, project))
      case None => projects
    }
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
    if (!ProjectApi.canManage(userOid, project))
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
    if (!ProjectApi.canManage(userOid, project))
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

  def updateGoogleDriveTags(projectId: String, documentId: String, tagNames: Seq[String], operation: String): Unit = {
    BWLogger.log(getClass.getName, "updateGoogleDriveTags", s"ENTRY ($projectId, $documentId, $tagNames, $operation)")
    val files = GoogleDrive.listObjects(Some(s"$projectId-$documentId"))
    for (file <- files) {
      val existingProperties = file.properties
      val (tags, others) = existingProperties.partition(_._1 == "tags")
      val existingTagSet: Set[String] = tags("tags").split(",").filterNot(t => t.trim.isEmpty).toSet
      val newTagSet: Set[String] = operation match {
        case "add" => existingTagSet ++ tagNames
        case "remove" => existingTagSet -- tagNames
        case _ => throw new IllegalArgumentException(s"bad tags operation: '$operation'")
      }
      val newProperties = Map("tags" -> newTagSet.mkString(",")) ++ others
      BWLogger.log(getClass.getName, "updateGoogleDriveTags", s"properties: ($existingProperties, $newProperties)")
      GoogleDrive.updateObjectById(file.id, newProperties)
      BWLogger.log(getClass.getName, "updateGoogleDriveTags", "EXIT-OK")
    }
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
        updateGoogleDriveTags(project._id[ObjectId].toString, docOid.toString, tagNames, "add")
      }
    } else if (operation == "remove") {
      for (docOid <- documentOids) {
        val updateResult = BWMongoDB3.document_master.updateOne(Map("_id" -> docOid),
          Map("$pullAll" -> Map("labels" -> tagNames)))
        if (updateResult.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
        updateGoogleDriveTags(project._id[ObjectId].toString, docOid.toString, tagNames, "remove")
      }
    } else
      throw new IllegalArgumentException(s"Bad operation: '$operation'")
  }

  def hasZombies(project: DynDoc): Boolean = allPhases(project).exists(PhaseApi.hasZombies)

  def displayStatus2(project: DynDoc, viewerIsAdmin: Boolean = false): String = {
    (project.status[String], isActive(project), hasZombies(project), viewerIsAdmin) match {
      case ("defined", _, _, _) => "Not started"
      case ("ended", _, _, _) => "Completed"
      // non-admins ...
      case (_, _, false, false) => "Active"
      case (_, _, true, false) => "Active+Error"
      // admin, non-zombie ...
      case (_, true, false, true) => "Active"
      case (_, false, false, true) => "Dormant"
      // admin, zombie ...
      case (_, true, true, true) => "Active+Error"
      case (_, false, true, true) => "Error"
    }
  }

  def displayStatus(project: DynDoc): String = {
    (isActive(project), hasZombies(project)) match {
      case (false, false) => "dormant"
      case (false, true) => "has-error"
      case (true, false) => "active"
      case (true, true) => "active+error"
    }
  }

  def validateNewName(newProjectName: String): Boolean = {
    val projectNameLength = newProjectName.length
    if (newProjectName.trim.length != projectNameLength)
      throw new IllegalArgumentException(s"Bad project name (has blank padding): '$newProjectName'")
    if (projectNameLength > 30 || projectNameLength < 5)
      throw new IllegalArgumentException(s"Bad project name length: $projectNameLength (must be 5-150)")
    if (fetch(name=Some(newProjectName)).nonEmpty)
      throw new IllegalArgumentException(s"Project named '$newProjectName' already exists")
    true
  }

  def timeZone(project: DynDoc): String = {
    if (project.has("tz"))
      project.tz[String]
    else
      PersonApi.personById(project.admin_person_id[ObjectId]).tz[String]
  }

  def managers(projectIn: Either[ObjectId, DynDoc]): Seq[ObjectId] = {
    val project: DynDoc = projectIn match {
      case Right(projObj) => projObj
      case Left(projOid) => projectById(projOid)
    }
    project.assigned_roles[Many[Document]].filter(_.role_name[String].matches(".*(?i)manager")).
        map(_.person_id[ObjectId]).distinct
  }

  def imageUrl(projectIn: Either[ObjectId, DynDoc]): String = {
    val projectOid: ObjectId = projectIn match {
      case Right(projObj) => projObj._id[ObjectId]
      case Left(projOid) => projOid
    }
    if (BWMongoDB3.images.countDocuments(Map("project_id" -> projectOid)) > 0) {
      s"../bw-3.01/baf3/ProjectInfoImage?project_id=$projectOid"
    } else {
      projectOid.toString.replaceAll("[a-fA-F]", "1").substring(12).toLong % 4 match {
        case 0 => "https://wp.technologyreview.com/wp-content/uploads/2020/04/cropped-MJ20_MIT_building.jpg"
        case 1 => "https://www.richardmeier.com/wp-content/uploads/2015/09/RH2660-0042.jpg"
        case 2 => "https://www.richardmeier.com/wp-content/uploads/2019/06/SRD0765_FirstAve_S010_EXT_Superman_Final2000.jpg"
        case 3 => "https://www.richardmeier.com/wp-content/uploads/2016/05/SRD0765_FirstAve_S030_EXT_EntryApproachParking_Final2000-1024x728.jpg"
      }
    }
  }

}