package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.DateTimeUtils
import org.bson.Document
import org.bson.types.ObjectId

import scala.annotation.tailrec

object ActivityApi extends DateTimeUtils {

  def activitiesByIds(activityOids: Seq[ObjectId], filter: Map[String, Any] = Map.empty): Seq[DynDoc] =
    BWMongoDB3.tasks.find(Map("_id" -> Map($in -> activityOids)) ++ filter)

  def activityById(activityOid: ObjectId): DynDoc = {
    BWMongoDB3.tasks.find(Map("_id" -> activityOid)).headOption match {
      case None => throw new IllegalArgumentException(s"Bad activity-id: $activityOid")
      case Some(activity) => activity
    }
  }

  def exists(activityOid: ObjectId): Boolean = BWMongoDB3.tasks.find(Map("_id" -> activityOid)).nonEmpty

  def allActions(activity: DynDoc) = Seq.empty[DynDoc] //activity.actions[Many[Document]]

  def allActions(activityOid: ObjectId) = Seq.empty[DynDoc] //: Seq[DynDoc] = allActions(activityById(activityOid))

  def allDeliverables3(activityOid: ObjectId): Seq[DynDoc] =
      BWMongoDB3.deliverables.find(Map("activity_id" -> activityOid))

  def actionsByUser(userOid: ObjectId): Seq[DynDoc] = {
    val activities: Seq[DynDoc] = BWMongoDB3.tasks.find()
    val actions: Seq[DynDoc] = activities.flatMap(activity => {
      val actions: Many[Document] = Seq.empty[Document] //= activity.actions[Many[Document]]
      actions.foreach(_.activity_id = activity._id[ObjectId])
      actions
    })
    actions.filter(_.assignee_person_id[ObjectId] == userOid)
  }

  def parentProcess(activityOid: ObjectId): DynDoc = {
    BWMongoDB3.processes.find(Map("activity_ids" -> activityOid)).head
  }

  def hasRole(personOid: ObjectId, activity: DynDoc): Boolean = {
    teamAssignment.list(activity._id[ObjectId]).
        exists(assignment => assignment.has("person_id") && assignment.person_id[ObjectId] == personOid)
  }

  def displayStatus2(activity: DynDoc): String = {
    activity.status[String] match {
      case "defined" => "Not started"
      case "running" => "Active"
      case "ended" => "Completed"
      case _ => "Error"
    }
  }

  def scheduledStart(activity: DynDoc): Option[Long] = {
    if (activity.has("bpmn_scheduled_start_date")) {
      val date = activity.bpmn_scheduled_start_date[Long]
      if (date == -1)
        None
      else
        Some(date)
    } else
      None
  }

  def scheduledStart31(phase: DynDoc, activity: DynDoc): Option[Long] = {
    try {
      val phaseTimestamps: Option[DynDoc] = phase.get[Document]("timestamps")
      val phaseStartDate: Option[Long] = phaseTimestamps.flatMap(_.get[Long]("date_start_estimated"))
      val activityOffset: Option[Long] = activity.get[Long]("offset")
      phaseStartDate.flatMap(psd => activityOffset.map(off => addWeekdays(psd, off, PhaseApi.timeZone(phase))))
    } catch {
      case _: Throwable => None
    }
  }

  def scheduledEnd31(phase: DynDoc, activity: DynDoc): Option[Long] = {
    val activityDurations: Option[DynDoc] = activity.get[Document]("durations")
    val activityLikelyDuration: Option[Long] =
        activityDurations.flatMap(d => d.likely[Int] match {case -1 => None; case d => Some(d)})
    scheduledStart31(phase, activity).flatMap(startDate => activityLikelyDuration.
        map(dur => addWeekdays(startDate, math.max(0, dur - 1), PhaseApi.timeZone(phase))))
  }

  def scheduledStart3(process: DynDoc, activity: DynDoc): Option[Long] = {
    (process.get[Long]("estimated_start_date"), activity.get[Long]("offset")) match {
      case (Some(processStartDate), Some(offset)) =>
        val phase = ProcessApi.parentPhase(process._id[ObjectId])
        Some(addWeekdays(processStartDate, offset, PhaseApi.timeZone(phase)))
      case _ => None
    }
  }

  def scheduledEnd(activity: DynDoc): Option[Long] = {
    if (activity.has("bpmn_scheduled_end_date")) {
      val date = activity.bpmn_scheduled_end_date[Long]
      if (date == -1)
        None
      else
        Some(date)
    } else
      None
  }

  def scheduledEnd3(process: DynDoc, activity: DynDoc): Option[Long] = {
    (scheduledStart3(process, activity), activity.get[Long]("duration")) match {
      case (Some(scheduledStartDate), Some(offset)) =>
        val phase = ProcessApi.parentPhase(process._id[ObjectId])
        Some(addWeekdays(scheduledStartDate, math.max(0, offset - 1), PhaseApi.timeZone(phase)))
      case _ => None
    }
  }

  def scheduledDuration(activity: DynDoc): Float = {
    (scheduledStart(activity), scheduledEnd(activity)) match {
      case (Some(start), Some(end)) => (end - start).toFloat / 86400000L
      case _ => -1
    }
  }

  def actualStart(activity: DynDoc): Option[Long] = {

    def timestampStart: Option[Long] = {
      if (activity.has("timestamps")) {
        val timestamps: DynDoc = activity.timestamps[Document]
        if (timestamps.has("start"))
          Some(timestamps.start[Long])
        else
          None
      } else
        None
    }

    if (activity.has("bpmn_actual_start_date")) {
      val date = activity.bpmn_actual_start_date[Long]
      if (date == -1)
        timestampStart
      else
        Some(date)
    } else
      timestampStart
  }

  def actualStart3(activity: DynDoc): Option[Long] = {
    if (activity.has("timestamps")) {
      val timestamps: DynDoc = activity.timestamps[Document]
      if (timestamps.has("start"))
        Some(timestamps.start[Long])
      else
        None
    } else
      None
  }

  def actualEnd(activity: DynDoc): Option[Long] = {

    def timestampEnd: Option[Long] = {
      if (activity.has("timestamps")) {
        val timestamps: DynDoc = activity.timestamps[Document]
        if (timestamps.has("end"))
          Some(timestamps.end[Long])
        else
          None
      } else
        None
    }

    if (activity.has("bpmn_actual_end_date")) {
      val date = activity.bpmn_actual_end_date[Long]
      if (date == -1)
        timestampEnd
      else
        Some(date)
    } else
      timestampEnd
  }

  def actualEnd3(activity: DynDoc): Option[Long] = {
    if (activity.has("timestamps")) {
      val timestamps: DynDoc = activity.timestamps[Document]
      if (timestamps.has("end"))
        Some(timestamps.end[Long])
      else
        None
    } else
      None
  }

  def actualDuration(activity: DynDoc): Float = {
    (actualStart(activity), actualEnd(activity)) match {
      case (Some(start), Some(end)) => (end - start).toFloat / 86400000L
      case _ => -1
    }
  }

  def changeLogItems(user: DynDoc, theActivity: DynDoc): Seq[Document] = {
    val changeLogEntries: Seq[DynDoc] = if (theActivity.has("change_log"))
      theActivity.change_log[Many[Document]]
    else
      Seq.empty[DynDoc]
    changeLogEntries.map(entry => {
      val dateTime = dateTimeString(entry.timestamp[Long], Some(user.tz[String]))
      val updatedBy = if (entry.has("updater_person_id")) {
        val updaterOid = entry.updater_person_id[ObjectId]
        val updater = PersonApi.personById(updaterOid)
        s"${updater.first_name} ${updater.last_name}"
      } else
        "-"
      val percentComplete = if (entry.has("percent_complete")) entry.percent_complete[Any].toString else "0"
      new Document("date_time", dateTime).append("updated_by", updatedBy).append("percent_complete", percentComplete).
        append("description", entry.description[String])
    }).reverse
  }

  def percentComplete(theActivity: DynDoc): String = {
    val changeLogEntries: Seq[DynDoc] = if (theActivity.has("change_log")) {
      theActivity.change_log[Many[Document]]
    } else {
      Seq.empty[DynDoc]
    }
    changeLogEntries.filter(_.has("percent_complete")).lastOption match {
      case Some(logEntry) => logEntry.percent_complete[Any].toString
      case None => "0"
    }
  }

  def addChangeLogEntry(activityOid: ObjectId, description: String, userOid: Option[ObjectId] = None,
      percentComplete: Option[String] = None): Unit = {
    userOid.map(PersonApi.exists) match {
      case Some(false) => throw new IllegalArgumentException(s"Bad user-id: '${userOid.get}'")
      case _ => // Ok
    }
    val timestamp = System.currentTimeMillis
    val changeLogEntry = (userOid, percentComplete) match {
      case (None, None) => Map("timestamp" -> timestamp, "description" -> description)
      case (Some(updaterOid), None) =>
        Map("timestamp" -> timestamp, "updater_person_id" -> updaterOid, "description" -> description)
      case (Some(updaterOid), Some(pct)) =>
        if (pct.toInt < 0 || pct.toInt > 100)
          throw new IllegalArgumentException(s"Bad percent-complete: $pct")
        Map("timestamp" -> timestamp, "updater_person_id" -> updaterOid, "description" -> description,
          "percent_complete" -> pct)
      case (None, Some(_)) => // Never possible!
    }
    val updateResult = BWMongoDB3.tasks.
      updateOne(Map("_id" -> activityOid), Map("$push" -> Map("change_log" -> changeLogEntry)))
    if (updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
  }

  def userAccessLevel(user: DynDoc, activity: DynDoc): String = {
    val assignments = teamAssignment.list(activity._id[ObjectId])
    val activeAssignments = assignments.
        filter(a => a.has("status") && a.status[String].matches("active|started"))
    if (PersonApi.isBuildWhizAdmin(Right(user))) {
      if (activeAssignments.nonEmpty)
        "all"
      else
        "manage"
    } else {
      val process = parentProcess(activity._id[ObjectId])
      if (ProcessApi.canManage(user._id[ObjectId], process)) {
        if (activeAssignments.nonEmpty)
          "all"
        else
          "manage"
      } else if (activeAssignments.exists(a => a.has("person_id") &&
          a.person_id[ObjectId] == user._id[ObjectId])) {
        "contribute"
      } else {
        "none"
      }
    }
  }

  def startedByBpmnEngine(activityOid: ObjectId): Unit = {
    ActivityApi.addChangeLogEntry(activityOid, s"Started Execution")
    val assignments = teamAssignment.list(activityOid)
    val preApprovals = assignments.filter(_.role[String] == RoleListSecondary.preApproval)
    if (preApprovals.nonEmpty) {
      for (preApproval <- preApprovals) {
        teamAssignment.assignmentStart(preApproval._id[ObjectId])
      }
    } else {
      assignments.find(a => !RoleListSecondary.secondaryRoles.contains(a.role[String])) match {
        case Some(primaryAssignment) =>
          teamAssignment.assignmentStart(primaryAssignment._id[ObjectId])
        case None =>
          throw new IllegalArgumentException(s"No primary assignment activity_id=$activityOid")
      }
    }
  }

  def stateSubState(theActivity: DynDoc): String = {
    if (theActivity.status[String] == "running") {
      val (preApprovals, postApprovals) = teamAssignment.list(theActivity._id[ObjectId]).
          filter(_.role[String].matches("(?:Pre|Post)-Approval")).
          partition(_.role[String] == RoleListSecondary.preApproval)
      if (preApprovals.exists(_.status[String] != "ended")) {
        RoleListSecondary.preApproval.toLowerCase
      } else if (postApprovals.exists(_.status[String] == "started")) {
        RoleListSecondary.postApproval.toLowerCase
      } else {
        "active"
      }
    } else
      theActivity.status[String]
  }

  def isDelayed(activity: DynDoc): Boolean = activity.has("is_delayed") && activity.is_delayed[Boolean]

  def setDelayed(activity: DynDoc, delayed: Boolean): Unit = {
    (isDelayed(activity), delayed) match {
      case (true, false) =>
        BWMongoDB3.tasks.updateOne(Map("_id" -> activity._id[ObjectId]), Map($unset -> Map("is_delayed" -> true)))
      case (false, true) =>
        BWMongoDB3.tasks.updateOne(Map("_id" -> activity._id[ObjectId]), Map($set -> Map("is_delayed" -> true)))
      case _ => // do nothing
    }
  }

  def managers(activity: DynDoc): Seq[ObjectId] = {
    val process = parentProcess(activity._id[ObjectId])
    ProcessApi.managers(process)
  }

  def canManage(personOid: ObjectId, activity: DynDoc): Boolean = {
    val process = parentProcess(activity._id[ObjectId])
    val phase = ProcessApi.parentPhase(process._id[ObjectId])
    PhaseApi.canManage(personOid, phase)
  }

  def durationOptimistic3(activity: DynDoc): Option[Int] = {
    val durations: Option[DynDoc] = activity.get[Document]("durations")
    durations.flatMap(_.get[Int]("optimistic") match {case Some(-1) => None; case someOtherD => someOtherD})
  }
  def durationPessimistic3(activity: DynDoc): Option[Int] = {
    val durations: Option[DynDoc] = activity.get[Document]("durations")
    durations.flatMap(_.get[Int]("pessimistic") match {case Some(-1) => None; case someOtherD => someOtherD})
  }
  def durationLikely3(activity: DynDoc): Option[Int] = {
    val durations: Option[DynDoc] = activity.get[Document]("durations")
    durations.flatMap(_.get[Int]("likely") match {case Some(-1) => None; case someOtherD => someOtherD})
  }

  def durationsSet3(activityOids: Seq[ObjectId], optDurationOptimistic: Option[Int] = None,
      optDurationPessimistic: Option[Int] = None, optDurationLikely: Option[Int] = None): Unit = {
    val setters: Seq[(String, Int)] = Seq(("duration_optimistic", optDurationOptimistic),
        ("duration_pessimistic", optDurationPessimistic), ("duration_likely", optDurationLikely)).flatMap {
      case (fieldName, Some(duration)) => Some((fieldName.replace("duration_", "durations."), duration))
      case (_, None) => None
    }
    val updateResult = BWMongoDB3.tasks.updateMany(Map("_id" -> Map($in -> activityOids)),
        Map($set -> setters.toMap))
    if (updateResult.getMatchedCount != activityOids.length)
      throw new IllegalArgumentException(s"MongoDB failed to match ${activityOids.length} Oid(s): $updateResult")
  }

  object teamAssignment {

    private def assignmentToString(assignment: Either[DynDoc, ObjectId]): String = {
      val theAssignment: DynDoc = assignment match {
        case Left(a) => a
        case Right(oid) => BWMongoDB3.activity_assignments.find(Map("_id" -> oid)).head
      }
      val role = theAssignment.role[String]
      val orgName = if (theAssignment.has("organization_id")) {
        val orgOid = theAssignment.organization_id[ObjectId]
        val theOrganization: DynDoc = BWMongoDB3.organizations.find(Map("_id" -> orgOid)).head
        theOrganization.name[String]
      } else {
        "NA"
      }
      val personName = if (theAssignment.has("person_id")) {
        val personOid = theAssignment.person_id[ObjectId]
        val thePerson: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
        PersonApi.fullName(thePerson)
      } else {
        "NA"
      }
      val individualRole = if (theAssignment.has("individual_role")) {
        theAssignment.individual_role[Many[String]].mkString(", ")
      } else {
        "NA"
      }
      s"Role: $role, Org: $orgName, Person: $personName, Individual-Role: $individualRole"
    }

    private def parentFields(activityOid: ObjectId): Map[String, Any] = {
      val process = parentProcess(activityOid)
      val processOid = process._id[ObjectId]
      val phase = ProcessApi.parentPhase(processOid)
      val phaseOid = phase._id[ObjectId]
      val project = PhaseApi.parentProject(phaseOid)
      Map("project_id" -> project._id[ObjectId], "phase_id" -> phaseOid, "process_id" -> processOid)
    }

    @tailrec
    def list(activityOid: ObjectId): Seq[DynDoc] = {
      val assignments: Seq[DynDoc] = BWMongoDB3.activity_assignments.find(Map("activity_id" -> activityOid))
      if (assignments.nonEmpty) {
        assignments
      } else {
        val theActivity = activityById(activityOid)
        BWMongoDB3.activity_assignments.insertOne(parentFields(activityOid) ++
            Map("activity_id" -> activityOid, "role" -> theActivity.role[String], "status" -> "defined"))
        teamAssignment.list(activityOid)
      }
    }

    def roleAdd(activityOid: ObjectId, roleName: String, optOrganizationId: Option[ObjectId], userOid: ObjectId):
        Unit = {
      val idAndStatus = Map("activity_id" -> activityOid, "role" -> roleName, "status" -> "defined")
      val baseRecord = idAndStatus ++ parentFields(activityOid)
      val fullRecord: Map[String, Any] = optOrganizationId match {
        case None => baseRecord
        case Some(oid) => baseRecord ++ Map("organization_id" -> oid)
      }
      BWMongoDB3.activity_assignments.insertOne(fullRecord)
      val message = s"Added role to (${assignmentToString(Left(fullRecord))})"
      addChangeLogEntry(activityOid, message, Some(userOid), None)
    }

    def organizationAdd(activityOid: ObjectId, roleName: String, organizationOid: ObjectId, userOid: ObjectId): Unit = {
      val assignments: Seq[DynDoc] = BWMongoDB3.activity_assignments.
        find(Map("activity_id" -> activityOid, "role" -> roleName, "organization_id" -> Map("$exists" -> false)))
      val assignment = assignments.head
      val assignmentOid = assignment._id[ObjectId]
      if (assignments.length == 1) {
        val updateResult = BWMongoDB3.activity_assignments.updateOne(Map("_id" -> assignmentOid),
          Map("$set" -> Map("organization_id" -> organizationOid)))
        if (updateResult.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      } else
        throw new IllegalArgumentException("Wrong state for adding organization")
      val message = s"Added organization to (${assignmentToString(Left(assignment))})"
      addChangeLogEntry(activityOid, message, Some(userOid), None)
    }

    def personAdd(activityOid: ObjectId, roleName: String, organizationOid: ObjectId, personOid: ObjectId,
        individualRole: Seq[String], documentAccess: Seq[String], userOid: ObjectId): Unit = {

      val query = Map("activity_id" -> activityOid, "role" -> roleName, "organization_id" -> organizationOid/*,
          "person_id" -> Map("$exists" -> false)*/)
      val baseRecord = query ++ parentFields(activityOid)
      val fullRecord = baseRecord ++ Map("person_id" -> personOid, "individual_role" -> individualRole,
          "document_access" -> documentAccess, "status" -> "defined")
      val assignments: Seq[DynDoc] = BWMongoDB3.activity_assignments.find(query)
      assignments.length match {
        case 0 => throw new IllegalArgumentException("Role and organization must be added first")
        case _ =>
          assignments.find(!_.has("person_id")) match {
            case Some(freeAssignment) =>
              val freeAssignmentOid = freeAssignment._id[ObjectId]
              val updateResult = BWMongoDB3.activity_assignments.updateOne(Map("_id" -> freeAssignmentOid),
                Map("$set" -> Map("person_id" -> personOid, "individual_role" -> individualRole,
                  "document_access" -> documentAccess)))
              if (updateResult.getMatchedCount == 0)
                throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
              val message = s"Added person, indiv-role to (${assignmentToString(Right(freeAssignmentOid))})"
              addChangeLogEntry(activityOid, message, Some(userOid), None)
            case None =>
              BWMongoDB3.activity_assignments.insertOne(fullRecord)
              val message = s"Created new assignment to (${assignmentToString(Left(fullRecord))})"
              addChangeLogEntry(activityOid, message, Some(userOid), None)
          }
      }
    }

    def documentAccessSet(activityOid: ObjectId, roleName: String, organizationOid: ObjectId,
        documentAccess: Seq[String]): Unit = {

      val query = Map("activity_id" -> activityOid, "role" -> roleName, "organization_id" -> organizationOid,
          "person_id" -> Map("$exists" -> true), "individual_role" -> Map("$exists" -> true))
      val assignments: Seq[DynDoc] = BWMongoDB3.activity_assignments.find(query)
      if (assignments.length == 1) {
        val assignment = assignments.head
        val assignmentOid = assignment._id[ObjectId]
        val updateResult = BWMongoDB3.activity_assignments.updateOne(Map("_id" -> assignmentOid),
          Map("$set" -> Map("document_access" -> documentAccess)))
        if (updateResult.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      } else
        throw new IllegalArgumentException("Bad assignment state")
    }

    def deleteAssignment(assignmentOid: ObjectId, userOid: ObjectId): Unit = {
      val theAssignment: DynDoc = BWMongoDB3.activity_assignments.find(Map("_id" -> assignmentOid)).head
      val activityOid = theAssignment.activity_id[ObjectId]
      if (RoleListSecondary.secondaryRoles.contains(theAssignment.role[String])) {
        val deleteResult = BWMongoDB3.activity_assignments.deleteOne(Map("_id" -> assignmentOid))
        if (deleteResult.getDeletedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $deleteResult")
      } else {
        val role = theAssignment.role[String]
        val count = BWMongoDB3.activity_assignments.countDocuments(Map("activity_id" -> activityOid, "role" -> role))
        if (count > 1) {
          val deleteResult = BWMongoDB3.activity_assignments.deleteOne(Map("_id" -> assignmentOid))
          if (deleteResult.getDeletedCount == 0)
            throw new IllegalArgumentException(s"MongoDB update failed: $deleteResult")
        } else if (count == 1) {
          val updateResult = BWMongoDB3.activity_assignments.updateOne(Map("_id" -> assignmentOid),
            Map("$unset" -> Map("organization_id" -> true, "person_id" -> true, "individual_role" -> true,
                "document_access" -> true)))
          if (updateResult.getMatchedCount == 0)
            throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
        } else
          throw new IllegalArgumentException("Severe system error")
      }
      val message = s"Deleted assignment (${assignmentToString(Left(theAssignment))})"
      addChangeLogEntry(activityOid, message, Some(userOid), None)
    }

    def assignmentStart(assignmentOid: ObjectId): Unit = {
      val updateResult = BWMongoDB3.activity_assignments.updateOne(Map("_id" -> assignmentOid),
        Map($set -> Map("status" -> "started", "timestamps" -> Map("start" -> System.currentTimeMillis))))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    }

    def assignmentEnd(assignmentOid: ObjectId): Unit = {
      val updateResult = BWMongoDB3.activity_assignments.updateOne(Map("_id" -> assignmentOid),
        Map($set -> Map("status" -> "ended", "timestamps.end" -> System.currentTimeMillis)))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    }

  }

}
