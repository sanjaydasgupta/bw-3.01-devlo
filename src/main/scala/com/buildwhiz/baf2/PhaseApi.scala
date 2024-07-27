package com.buildwhiz.baf2

import com.buildwhiz.baf3.DeliverableApi
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB, BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.BWLogger

import javax.servlet.http.HttpServletRequest
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.immutable.Map.WithDefault
import scala.jdk.CollectionConverters.SeqHasAsJava

object PhaseApi {

  def phasesByIds(phaseOids: Seq[ObjectId], db: BWMongoDB=BWMongoDB3): Seq[DynDoc] = {
    db.phases.find(Map("_id" -> Map($in -> phaseOids)))
  }

  def phaseById(phaseOid: ObjectId, db: BWMongoDB=BWMongoDB3): DynDoc = {
    db.phases.find(Map("_id" -> phaseOid)).headOption match {
      case Some(phase) => phase
      case None => throw new IllegalArgumentException(s"Bad phase _id: $phaseOid")
    }
  }

  def exists(phaseOid: ObjectId, db: BWMongoDB=BWMongoDB3): Boolean = {
    db.phases.find(Map("_id" -> phaseOid)).nonEmpty
  }

  def allProcessOids(phase: DynDoc, db: BWMongoDB=BWMongoDB3): Seq[ObjectId] = {
    phase.process_ids[Many[ObjectId]].filter(pOid => ProcessApi.exists(pOid, db))
  }

  def allProcesses(phase: DynDoc, db: BWMongoDB = BWMongoDB3): Seq[DynDoc] = {
    val processOids = allProcessOids(phase, db)
    ProcessApi.processesByIds(processOids, db)
  }

  def allProcesses2(phaseOid: ObjectId, db: BWMongoDB = BWMongoDB3): Seq[DynDoc] = {
    allProcesses(phaseById(phaseOid, db), db)
  }

  def allActivities(phaseIn: Either[ObjectId, DynDoc], filter: Map[String, Any] = Map.empty,
      db: BWMongoDB = BWMongoDB3): Seq[DynDoc] = {
    val phase = phaseIn match {
      case Right(ph) => ph
      case Left(oid) => phaseById(oid, db)
    }
    val activityOids: Seq[ObjectId] = allProcesses(phase, db).flatMap(_.activity_ids[Many[ObjectId]])
    ActivityApi.activitiesByIds(activityOids, filter, db)
  }

  def allActivities30(phaseIn: Either[ObjectId, DynDoc], db: BWMongoDB=BWMongoDB3): Seq[DynDoc] = {
    phaseIn match {
      case Left(phaseOid) =>
        db.phases.aggregate(Seq(
          new Document("$match", new Document("_id", phaseOid)),
          new Document("$unwind", new Document("path", "$process_ids").append("preserveNullAndEmptyArrays", false)),
          new Document("$limit", 1),
          new Document("$project", new Document("process_ids", 1)),
          new Document("$lookup", new Document("from", "processes").append("localField", "process_ids").
            append("foreignField", "_id").append("as", "processes")),
          new Document("$project", new Document("processes", 1)),
          new Document("$unwind", new Document("path", "$processes").append("preserveNullAndEmptyArrays", false)),
          new Document("$set", new Document("activity_ids", "$processes.activity_ids")),
          new Document("$unwind", new Document("path", "$activity_ids").append("preserveNullAndEmptyArrays", false)),
          new Document("$lookup", new Document("from", "tasks").append("localField", "activity_ids").
            append("foreignField", "_id").append("as", "activity")),
          new Document("$unwind", new Document("path", "$activity").append("preserveNullAndEmptyArrays", false)),
          new Document("$replaceRoot", new Document("newRoot", "$activity"))
        ))
      case Right(phaseRecord) =>
        db.processes.aggregate(Seq(
          new Document("$match", new Document("_id", new Document($in, phaseRecord.process_ids[Many[ObjectId]]))),
          new Document("$limit", 1),
          new Document("$unwind", new Document("path", "$activity_ids").append("preserveNullAndEmptyArrays", false)),
          new Document("$lookup", new Document("from", "tasks").append("localField", "activity_ids").
            append("foreignField", "_id").append("as", "activity")),
          new Document("$unwind", new Document("path", "$activity").append("preserveNullAndEmptyArrays", false)),
          new Document("$replaceRoot", new Document("newRoot", "$activity"))
        ))
    }
  }

  def isActive(phase: DynDoc, db: BWMongoDB = BWMongoDB3): Boolean = {
    allProcesses(phase, db).exists(process => ProcessApi.isActive(process))
  }

  def phaseLevelUsers(phase: DynDoc): Seq[ObjectId] = {
    if (phase.has("assigned_roles")) {
      /*(phase.admin_person_id[ObjectId] +:*/
          phase.assigned_roles[Many[Document]].map(_.person_id[ObjectId]).distinct
    } else {
      Seq.empty[ObjectId]/*Seq(phase.admin_person_id[ObjectId])*/
    }
  }

  def delete(phase: DynDoc, request: HttpServletRequest, db: BWMongoDB = BWMongoDB3): String = {
    val phaseOid = phase._id[ObjectId]
    if (isActive(phase, db)) {
      throw new IllegalArgumentException(s"Phase '${phase.name[String]}' is still active")
    }
    val teamOids: Many[ObjectId] = phase.team_assignments[Many[Document]].map(_.team_id[ObjectId]).asJava
    val teamsDeleteResult = db.teams.deleteMany(Map("_id" -> Map($in -> teamOids)))
    val deliverablesOidsPipe: Seq[DynDoc] = Seq(
      Map("$match" -> Map("phase_id" -> phaseOid)),
      Map("$group" -> Map("_id" -> null, "deliverable_ids" -> Map($push -> "$_id")))
    )
    val deliverableOids = db.deliverables.aggregate(deliverablesOidsPipe.map(_.asDoc).asJava).head.
      deliverable_ids[Many[ObjectId]]
    val constraintsDeleteResult = db.constraints.deleteMany(Map("owner_deliverable_id" -> Map($in -> deliverableOids)))
    val deliverablesDeleteResult = db.deliverables.deleteMany(Map("phase_id" -> phaseOid))
    val processesDeleteResult = db.processes.deleteOne(Map("parent_phase_id" -> phaseOid))
    val phaseDeleteResult = db.phases.deleteOne(Map("_id" -> phaseOid))
    val projectUpdateResult = if (phaseDeleteResult.getDeletedCount == 1) {
      db.projects.updateOne(Map("phase_ids" -> phaseOid),
        Map("$pull" -> Map("phase_ids" -> phaseOid))).toString
    } else {
      "No update"
    }
    val updates = Seq(
      s"teams: $teamsDeleteResult",
      s"constraints: $constraintsDeleteResult",
      s"deliverables: $deliverablesDeleteResult",
      s"processes: $processesDeleteResult",
      s"phase: $phaseDeleteResult",
      s"project: $projectUpdateResult"
    )
    val where = if (db == BWMongoDB3) "Host" else "Library"
    val message = updates.mkString(s"$where updates: [", "|", "]")
    message
  }

  def parentProject(phaseOid: ObjectId, db: BWMongoDB = BWMongoDB3): DynDoc = {
    db.projects.find(Map("phase_ids" -> phaseOid)).head
  }

  def allTeamOids30(phase: DynDoc): Seq[ObjectId] = {
    phase.get[Many[Document]]("team_assignments") match {
      case Some(ta) => ta.map(_.team_id[ObjectId])
      case None => Seq.empty[ObjectId]
    }
  }

  def hasRole30(personOid: ObjectId, phase: DynDoc): Boolean = {
    def hasTeamRole: Boolean = {
      val personsTeamOids: Set[ObjectId] = PersonApi.allTeams30(personOid).map(_._id[ObjectId]).toSet
      val phasesTeamOids: Set[ObjectId] = allTeamOids30(phase).toSet
      (personsTeamOids & phasesTeamOids).nonEmpty
    }
    val hasDirectRole = phase.assigned_roles[Many[Document]].exists(_.person_id[ObjectId] == personOid)
    hasDirectRole || hasTeamRole
  }

  def hasRole(personOid: ObjectId, phase: DynDoc): Boolean = {
    /*isAdmin(personOid, phase) || */phase.assigned_roles[Many[Document]].exists(_.person_id[ObjectId] == personOid) ||
      allProcesses(phase).exists(process => ProcessApi.hasRole(personOid, process))
  }

//  def isAdmin(personOid: ObjectId, phase: DynDoc): Boolean =
//    phase.admin_person_id[ObjectId] == personOid
//
  def isManager(personOid: ObjectId, phase: DynDoc): Boolean = phase.assigned_roles[Many[Document]].
    exists(ar => ar.person_id[ObjectId] == personOid &&
      ar.role_name[String].matches("(?i)(?:project-|phase-)?manager"))

  def canManage(personOid: ObjectId, phase: DynDoc): Boolean =
      isManager(personOid, phase) || /*isAdmin(personOid, phase) ||*/
      ProjectApi.canManage(personOid, parentProject(phase._id[ObjectId]))

  def phasesByUser(personOid: ObjectId, optParentProject: Option[DynDoc] = None, db: BWMongoDB=BWMongoDB3):
      Seq[DynDoc] = {
    optParentProject match {
      case Some(parentProject) =>
        val phaseOids: Seq[ObjectId] = parentProject.phase_ids[Many[ObjectId]]
        phasesByIds(phaseOids)
      case None =>
        val phases: Seq[DynDoc] = db.phases.find()
        phases.filter(phase => hasRole(personOid, phase))
    }
  }

  def hasZombies(phase: DynDoc): Boolean = allProcesses(phase).exists(ProcessApi.isZombie)

  def displayStatus(phase: DynDoc): String = {
    (phase.status[String], isActive(phase), hasZombies(phase)) match {
      case ("defined", false, false) => "Not Started"
      case ("ended", _, false) => "Complete"
      case ("ended", _, true) => "Complete+Error"
      case (_, false, false) => "Complete"
      case (_, false, true) => "Error"
      case (_, true, false) => "Active"
      case (_, true, true) => "Active+Error"
    }
  }

  def displayStatus2(phase: DynDoc): String = {
    displayStatus(phase)
  }

  val displayStatusOrdering31 = new WithDefault(Map("Active" -> 10, "Planning" -> 20, "Ended" -> 30), (_: Any) => 100)

  def displayStatus31(phase: DynDoc): String = {
    (phase.get[Boolean]("started"), phase.get[Boolean]("ended")) match {
      case (None, _) | (Some(false), _) =>
        "Planning"
      case (Some(true), None) | (Some(true), Some(false)) =>
        "Active"
      case (_, Some(true)) =>
        "Ended"
      case _ =>
        "Unknown"
    }
  }

  def displayStatus3(phase: DynDoc): String = {
    val started = phase.get[Boolean]("started") match {
      case Some(sv) => sv
      case None => false
    }
    val phaseStatusFromDeliverables = new WithDefault[String, String](Map("Completed" -> "Completed"), _ => "Active")
    if (started) {
      PhaseApi.allProcesses(phase).headOption match {
        case Some(theProcess) =>
          val activities = ProcessApi.allActivities(Right(theProcess))
          val deliverables = DeliverableApi.deliverablesByActivityOids(activities.map(_._id[ObjectId]))
          phaseStatusFromDeliverables(DeliverableApi.aggregateStatus(deliverables))
        case None => "Unknown"
      }
    } else {
      "Planning"
    }
  }

  def validateNewName(newPhaseName: String, projectOid: ObjectId, db: BWMongoDB=BWMongoDB3): Boolean = {
    val phaseNameLength = newPhaseName.length
    if (newPhaseName.trim.length != phaseNameLength)
      throw new IllegalArgumentException(s"Bad phase name (has blank padding): '$newPhaseName'")
    if (phaseNameLength > 150 || phaseNameLength < 5)
      throw new IllegalArgumentException(s"Bad phase name length: $phaseNameLength (must be 5-150)")
    val phaseOids: Seq[ObjectId] = ProjectApi.allPhaseOids(ProjectApi.projectById(projectOid))
    val count = db.phases.countDocuments(Map("name" -> newPhaseName, "_id" -> Map($in -> phaseOids)))
    if (count > 0)
      throw new IllegalArgumentException(s"Phase named '$newPhaseName' already exists")
    true
  }

  def managers(phaseIn: Either[ObjectId, DynDoc]): Seq[ObjectId] = {
    val phase: DynDoc = phaseIn match {
      case Right(phaseObj) => phaseObj
      case Left(phaseOid) => phaseById(phaseOid)
    }
    val phaseManagers = phase.assigned_roles[Many[Document]].
        filter(_.role_name[String].matches(".*(?i)manager")).map(_.person_id[ObjectId])
    phaseManagers.distinct
  }

  def timeZone(phase: DynDoc, optRequest: Option[HttpServletRequest] = None, db: BWMongoDB=BWMongoDB3): String = {
    if (phase.has("tz")) {
      phase.tz[String]
    } else {
      val parent = parentProject(phase._id[ObjectId])
      if (parent.has("tz")) {
        val tz = parent.tz[String]
        db.phases.updateOne(Map("_id" -> phase._id[ObjectId]), Map($set -> Map("tz" -> tz)))
        tz
      } else {
        val method = optRequest match {
          case None => "LOCAL"
          case Some(request) => request.getMethod
        }
        BWLogger.log(getClass.getName, method, s"WARN: NO timezone in project ${parent.name[String]}", optRequest)
        "GMT"
      }
    }
  }

  def getTaktUnitCount(phaseOid: ObjectId, bpmnNameFull: String, activityCount: Int, db: BWMongoDB=BWMongoDB3): Int = {
    // BEGIN Takt simplified approach
    val taktTempActivitiesCount =
      db.takt_temp_activities.countDocuments(Map("phase_id" -> phaseOid, "bpmn_name_full" -> bpmnNameFull))
    if (taktTempActivitiesCount == 0) {
      0
    } else {
      (taktTempActivitiesCount / activityCount).asInstanceOf[Int]
    }
    // END Takt simplified approach
  }

  def phaseDocumentTagName(phaseName: String): String = s"@phase($phaseName)"

}