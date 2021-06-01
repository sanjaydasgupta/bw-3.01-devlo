package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import BWMongoDB3._
import DynDoc._
import com.buildwhiz.baf3.TeamApi
import org.bson.types.ObjectId
import org.bson.Document
import com.buildwhiz.infra.GoogleDrive
import com.buildwhiz.utils.BWLogger

import scala.collection.JavaConverters._
import scala.compat.Platform.EOL
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object PersonApi {

  val possibleIndividualRoles: Seq[String] = Seq("Principal", "Admin", "Finance", "Contract", "Lead", "Contributor")

  def listAdmins: Seq[DynDoc] = BWMongoDB3.persons.find(Map("roles" -> "BW-Admin"))

  def personsByIds(personOids: Seq[ObjectId]): Seq[DynDoc] =
    BWMongoDB3.persons.find(Map("_id" -> Map($in -> personOids)))

  def personById(personOid: ObjectId): DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head

  def exists(personOid: ObjectId): Boolean = BWMongoDB3.persons.find(Map("_id" -> personOid)).nonEmpty

  def isBuildWhizAdmin(who: Either[ObjectId, DynDoc]): Boolean = {
    val userRecord = who match {
      case Right(dynDoc) => dynDoc
      case Left(oid) => personById(oid)
    }
    userRecord.roles[Many[String]].contains("BW-Admin")
  }

  def inSameOrganization(person1Oid: ObjectId, person2Oid: ObjectId): Boolean = {
    val person1 = personById(person1Oid)
    val person2 = personById(person2Oid)
    person1.has("organization_id") && person2.has("organization_id") &&
        person1.organization_id[ObjectId] == person2.organization_id[ObjectId]
  }

  def fetch3(optWorkEmail: Option[String] = None, optOrganizationOid: Option[ObjectId] = None,
      optSkill: Option[String] = None, optProjectOid: Option[ObjectId] = None, optPhaseOid: Option[ObjectId] = None,
      optProcessOid: Option[ObjectId] = None, optActivityOid: Option[ObjectId] = None): Seq[DynDoc] = {

    val query: Map[String, Any] = (optWorkEmail, optOrganizationOid, optSkill, optPhaseOid, optProjectOid) match {
      case (Some(workEmail), _, _, _, _) =>
        Map("emails" -> Map($elemMatch -> Map("type" -> "work", "email" -> workEmail)))
      case (None, Some(organizationOid), Some(skill), _, _) =>
        Map("organization_id" -> organizationOid, "skills" -> Map($regex -> s"(?i)^$skill\\s*(\\([^)]+\\))?$$"))
      case (None, Some(organizationOid), None, _, _) =>
        Map("organization_id" -> organizationOid)
      case (None, None, Some(skill), _, _) =>
        Map("skills" -> Map($regex -> s"(?i)^$skill\\s*(\\([^)]+\\))?$$"))
      case (None, None, None, Some(phaseOid), _) =>
        val allTeamOids = PhaseApi.allTeamOids30(PhaseApi.phaseById(phaseOid))
        val allMemberOids: Seq[ObjectId] = allTeamOids.flatMap(TeamApi.memberOids)
        Map("_id" -> Map($in -> allMemberOids))
      case (None, None, None, _, Some(projectOid)) =>
        val allPhases: Seq[DynDoc] = ProjectApi.allPhases(projectOid)
        val allTeamOids: Seq[ObjectId] = allPhases.flatMap(PhaseApi.allTeamOids30)
        val allMemberOids: Seq[ObjectId] = allTeamOids.flatMap(TeamApi.memberOids)
        Map("_id" -> Map($in -> allMemberOids))
      case _ => Map.empty
    }

    BWMongoDB3.persons.find(query)
  }

  def fetch(optWorkEmail: Option[String] = None, optOrganizationOid: Option[ObjectId] = None,
      optSkill: Option[String] = None, optProjectOid: Option[ObjectId] = None, optPhaseOid: Option[ObjectId] = None,
      optProcessOid: Option[ObjectId] = None, optActivityOid: Option[ObjectId] = None): Seq[DynDoc] = {
    def assigneeOid(assignment: DynDoc): Option[ObjectId] = {
        if (assignment.has("person_id")) Some(assignment.person_id[ObjectId]) else None
    }

    val assigneeOids: (Boolean, Seq[ObjectId]) = (optProjectOid, optPhaseOid, optProcessOid, optActivityOid) match {
      case (_, _, _, Some(activityOid)) =>
        (true, BWMongoDB3.activity_assignments.find(Map("activity_id" -> activityOid)).flatMap(assigneeOid))
      case (_, _, Some(processOid), None) =>
        (true, BWMongoDB3.activity_assignments.find(Map("process_id" -> processOid)).flatMap(assigneeOid))
//      case (_, Some(phaseOid), None, None) =>
//        (true, BWMongoDB3.activity_assignments.find(Map("phase_id" -> phaseOid)).flatMap(assigneeOid))
//      case (Some(projectOid), None, None, None) =>
//        (true, BWMongoDB3.activity_assignments.find(Map("project_id" -> projectOid)).flatMap(assigneeOid))
      case _ => (false, Nil)
    }
    val query1: Map[String, Any] = (optWorkEmail, optOrganizationOid, optSkill) match {
      case (Some(workEmail), _, _) =>
        Map("emails" -> Map($elemMatch -> Map("$eq" -> Map("type" -> "work", "email" -> workEmail))))
      case (None, Some(organizationOid), Some(skill)) =>
        Map("organization_id" -> organizationOid, "skills" -> Map($regex -> s"(?i)^$skill\\s*(\\([^)]+\\))?$$"))
      case (None, Some(organizationOid), None) =>
        Map("organization_id" -> organizationOid)
      case (None, None, Some(skill)) =>
        Map("skills" -> Map($regex -> s"(?i)^$skill\\s*(\\([^)]+\\))?$$"))
      case _ => Map.empty
    }
    val query2: Map[String, Any] = if (assigneeOids._1)
      query1 ++ Map("_id" -> Map($in -> assigneeOids._2.distinct))
    else
      query1
    BWMongoDB3.persons.find(query2)
  }

  def deleteDocumentTag(personOid: ObjectId, tagName: String): Unit = {
    val userRecord: DynDoc = personById(personOid)
    val userTags: Seq[DynDoc] = documentTags(userRecord)
    if (!userTags.exists(_.name[String] == tagName))
      throw new IllegalArgumentException(s"Unknown tag '$tagName'")
    val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> personOid),
      Map("$pull" -> Map("labels" -> Map("name" -> tagName))))
    if (updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    BWMongoDB3.persons.updateOne(Map("_id" -> personOid,
      "document_filter_labels" -> Map("$exists" -> true)),
      Map("$pull" -> Map("document_filter_labels" -> tagName)))
  }

  def addDocumentTag(personOid: ObjectId, tagName: String, optLogic: Option[String]): Unit = {
    val userRecord: DynDoc = PersonApi.personById(personOid)
    val userTags: Seq[DynDoc] = documentTags(userRecord)
    if (userTags.exists(_.name[String] == tagName))
      throw new IllegalArgumentException(s"Tag '$tagName' already exists")
    val tagRecord = optLogic match {
      case None => Map("name" -> tagName, "document_ids" -> Seq.empty[ObjectId])
      case Some(logic) => Map("name" -> tagName, "document_ids" -> Seq.empty[ObjectId], "logic" -> logic)
    }
    val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> personOid),
      Map("$push" -> Map("labels" -> tagRecord)))
    if (updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
  }

  def fullName(person: DynDoc): String = s"${person.first_name[String]} ${person.last_name[String]}"

  def documentTags(person: DynDoc): Seq[DynDoc] = {
    if (person.has("labels")) {
      person.labels[Many[Document]]
    } else {
      Seq.empty[Document]
    }
  }

  def documentGroupManageLabels(person: DynDoc, documentOids: Seq[ObjectId], tagNames: Seq[String],
      operation: String): Unit = {
    val allUserTags = documentTags(person)
    val badTagNames = tagNames.filter(tagName => !allUserTags.exists(_.name[String] == tagName))
    if (badTagNames.nonEmpty)
      throw new IllegalArgumentException(s"""Bad tag-names: ${badTagNames.mkString(", ")}""")

    val badDocumentOids = documentOids.filter(oid => !DocumentApi.exists(oid))
    if (badDocumentOids.nonEmpty)
      throw new IllegalArgumentException(s"""Bad document-ids: ${badDocumentOids.mkString(", ")}""")

    if (operation == "add") {
      tagNames.map(tagName => person.labels[Many[Document]].indexWhere(_.name[String] == tagName)).foreach(idx => {
        val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> person._id[ObjectId]),
          Map("$addToSet" -> Map(s"labels.$idx.document_ids" -> Map("$each" -> documentOids))))
        if (updateResult.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      })
    } else if (operation == "remove") {
      tagNames.map(tagName => person.labels[Many[Document]].indexWhere(_.name[String] == tagName)).foreach(idx => {
        val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> person._id[ObjectId]),
          Map("$pullAll" -> Map(s"labels.$idx.document_ids" -> documentOids)))
        if (updateResult.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      })
    } else
      throw new IllegalArgumentException(s"Bad operation: '$operation'")
  }

  def person2document(person: DynDoc): Document = {
    val name = fullName(person)
    val active = if (person.has("enabled")) person.enabled[Boolean] else false
    val position = person.get[String]("position") match {
      case Some(pos) => pos
      case None => ""
    }
    val individualRoles: java.util.Collection[String] = if (person.has("individual_roles"))
      person.individual_roles[Many[String]]
    else
      Seq.empty[String].asJava

    new Document("_id", person._id[ObjectId]).append("name", name).append("skills", person.skills[Many[String]]).
        append("years_experience", person.years_experience[Int]).append("rating", person.rating[Double]).
        append("active", active).append("individual_roles", individualRoles).append("position", position)
  }

  def parentOrganization(personIn: Either[ObjectId, DynDoc]): Option[DynDoc] = {
    val person = personIn match {
      case Right(pd) => pd
      case Left(pOid) => personById(pOid)
    }
    if (person.has("organization_id")) {
      val organization = OrganizationApi.organizationById(person.organization_id[ObjectId])
      Some(organization)
    } else {
      None
    }
  }

  def vCard(personIn: Either[ObjectId, DynDoc]): String = {
    val person = personIn match {
      case Right(pd) => pd
      case Left(pOid) => personById(pOid)
    }
    val (firstName, lastName) = (person.first_name[String], person.last_name[String])
    val n = s"N:$lastName;$firstName;;;"
    val fn = s"FN:$firstName $lastName"
    val org = parentOrganization(Right(person)) match {
      case Some(orgRecord) => s"ORG:${orgRecord.name[String]}"
      case None => ""
    }
    val workEmailRecord: DynDoc = person.emails[Many[Document]].find(_.`type`[String] == "work").get
    val workEmail = s"EMAIL:${workEmailRecord.email[String]}"
    val workPhoneRecord: Option[DynDoc] = person.phones[Many[Document]].find(_.`type`[String] == "work")
    val workPhone = workPhoneRecord match {
      case None => ""
      case Some(wp) => s"TEL;TYPE=WORK,VOICE:${wp.phone[String]}"
    }
    val parts = Seq("BEGIN:VCARD", "VERSION:3.0", n, fn, org, workEmail, workPhone, "END:VCARD")
    parts.filter(_.nonEmpty).mkString("\n")
  }

  def validateNewName(firstName: String, lastName: String, orgOid: ObjectId): Boolean = {
    val nameTagValues = Seq(("first", firstName), ("last", lastName))
    for ((tag, value) <- nameTagValues) {
      val nameLength = value.length
      if (value.trim.length != nameLength)
        throw new IllegalArgumentException(s"Bad $tag-name (has blank padding): '$value'")
      if (nameLength > 30 || nameLength == 0)
        throw new IllegalArgumentException(s"Bad $tag-name length: $nameLength")
    }
    val others = fetch(optOrganizationOid = Some(orgOid))
    if (others.exists(other => other.first_name[String] == firstName && other.last_name[String] == lastName))
      throw new IllegalArgumentException(s"Name conflicts with another person in same organization")
    true
  }

  def allTeams30(personOid: ObjectId): Seq[DynDoc] = {
    BWMongoDB3.teams.find(Map("team_members" -> Map($elemMatch -> Map("person_id" -> personOid))))
  }

  def allRoles(person: DynDoc): Seq[String] = person.roles[Many[String]]

  def userGDriveFolderName(user: DynDoc) = s"${fullName(user)} (${user._id[ObjectId]})"

  def userGoogleLoginEmail(user: DynDoc): String = {
    if (user.emails[Many[Document]].exists(_.`type`[String] == "google")) {
      user.emails[Many[Document]].find(_.`type`[String] == "google").map(_.email[String]).head
    } else {
      user.emails[Many[Document]].find(_.`type`[String] == "work").map(_.email[String]).head
    }
  }

  def userGDriveFolderId(user: DynDoc): String = {
    if (user.has("g_drive_folder_id")) {
      user.g_drive_folder_id[String]
    } else {
      val folderName = userGDriveFolderName(user)
      val userFolderId = GoogleDrive.getUserFolderId(folderName, userGoogleLoginEmail(user))
      val userOid = user._id[ObjectId]
      BWMongoDB3.persons.updateOne(Map("_id" -> userOid), Map($set -> Map("g_drive_folder_id" -> userFolderId)))
      Future {populateGDriveFolder(user, userFolderId)}
      userFolderId
    }
  }

  def populateGDriveFolder(user: DynDoc, usersGFolderId: String): Unit = {
    val userOid = user._id[ObjectId]
    val fullName = s"${PersonApi.fullName(user)} ($userOid)"
    BWLogger.log(getClass.getName, s"populateGDriveFolder($fullName, $usersGFolderId)", s"ENTRY")
    try {
      val usersAssignments: Seq[DynDoc] = BWMongoDB3.activity_assignments.find(Map("person_id" -> userOid))
      val projects2phases: Map[ObjectId, Seq[ObjectId]] = usersAssignments.groupBy(_.project_id[ObjectId]).
          map(p => (p._1, p._2.map(_.phase_id[ObjectId]).distinct))
      BWLogger.log(getClass.getName, s"populateGDriveFolder($fullName, $usersGFolderId)",
          s"Projects: ${projects2phases.size}")
      for ((projectOid, phaseOids) <- projects2phases) {
        val project = ProjectApi.projectById(projectOid)
        val projectName = project.name[String]
        lazy val gDriveProjectFolderId = GoogleDrive.
            getOrCreateFolder(usersGFolderId, projectName, userGoogleLoginEmail(user))
        lazy val projectFiles = GoogleDrive.listObjects(Some(s"$projectOid-"))
        BWLogger.log(getClass.getName, s"populateGDriveFolder($fullName, $usersGFolderId)",
            s"Project '$projectName' has ${projectFiles.length} files")
        val nonPhaseFiles = projectFiles.filterNot(_.properties.contains("phase"))
        BWLogger.log(getClass.getName, s"populateGDriveFolder($fullName, $usersGFolderId)",
          s"Project '$projectName' has ${nonPhaseFiles.length} non-phase files")
        for (nonPhaseFile <- nonPhaseFiles) {
          val timestamp = nonPhaseFile.key.split("-").last
          val nonPhaseFileName = s"${nonPhaseFile.properties("name")} ($timestamp)"
          GoogleDrive.createShortcut(gDriveProjectFolderId, nonPhaseFile.id, nonPhaseFileName)
        }
        for (phaseOid <- phaseOids) {
          val phase = PhaseApi.phaseById(phaseOid)
          val phaseName = phase.name[String]
          lazy val gDrivePhaseFolderId = GoogleDrive.
              getOrCreateFolder(gDriveProjectFolderId, phaseName, userGoogleLoginEmail(user))
          val phaseFiles = projectFiles.
              filter(file => file.properties.contains("phase") && file.properties("phase") == phaseName)
          BWLogger.log(getClass.getName, s"populateGDriveFolder($fullName, $usersGFolderId)",
              s"Phase '$phaseName' has ${phaseFiles.length} files")
          for (phaseFile <- phaseFiles) {
            val timestamp = phaseFile.key.split("-").last
            val phaseFileName = s"${phaseFile.properties("name")} ($timestamp)"
            GoogleDrive.createShortcut(gDrivePhaseFolderId, phaseFile.id, phaseFileName)
          }
        }
        BWLogger.log(getClass.getName, s"populateGDriveFolder($fullName, $usersGFolderId)", s"EXIT-OK")
      }
    } catch {
      case throwable: Throwable =>
        val stackTrace = throwable.getStackTrace.mkString("", EOL, EOL)
        BWLogger.log(getClass.getName, s"populateGDriveFolder($fullName, $usersGFolderId)",
            s"ERROR-Asynchronous ($stackTrace)")
    }
  }

  def userGDriveFolderUrl(user: DynDoc): String = {
    val userFolderId = userGDriveFolderId(user)
    s"https://drive.google.com/drive/folders/$userFolderId"
  }

}
