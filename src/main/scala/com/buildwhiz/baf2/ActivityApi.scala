package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import org.bson.Document
import org.bson.types.ObjectId

object ActivityApi {

  def activityById(activityOid: ObjectId): DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).headOption match {
    case None => throw new IllegalArgumentException(s"Bad activity-id: $activityOid")
    case Some(activity) => activity
  }

  def exists(activityOid: ObjectId): Boolean = BWMongoDB3.activities.find(Map("_id" -> activityOid)).nonEmpty

  def allActions(activity: DynDoc): Seq[DynDoc] = activity.actions[Many[Document]]

  def allActions(activityOid: ObjectId): Seq[DynDoc] = allActions(activityById(activityOid))

  def actionsByUser(userOid: ObjectId): Seq[DynDoc] = {
    val activities: Seq[DynDoc] = BWMongoDB3.activities.find()
    val actions: Seq[DynDoc] = activities.flatMap(activity => {
      val actions = activity.actions[Many[Document]]
      actions.foreach(_.activity_id = activity._id[ObjectId])
      actions
    })
    actions.filter(_.assignee_person_id[ObjectId] == userOid)
  }

  def parentProcess(activityOid: ObjectId): DynDoc = {
    BWMongoDB3.processes.find(Map("activity_ids" -> activityOid)).head
  }

  def hasRole(personOid: ObjectId, activity: DynDoc): Boolean =
    allActions(activity).exists(_.assignee_person_id[ObjectId] == personOid)

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

  def scheduledDuration(activity: DynDoc): Float = {
    (scheduledStart(activity), scheduledEnd(activity)) match {
      case (Some(start), Some(end)) => (end - start) / 86400000L
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

  def actualDuration(activity: DynDoc): Float = {
    (actualStart(activity), actualEnd(activity)) match {
      case (Some(start), Some(end)) => (end - start) / 86400000L
      case _ => -1
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
    val updateResult = BWMongoDB3.activities.
      updateOne(Map("_id" -> activityOid), Map("$push" -> Map("change_log" -> changeLogEntry)))
    if (updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
  }

  def userAccessLevel(user: DynDoc, activity: DynDoc, action: DynDoc): String = {
    if (PersonApi.isBuildWhizAdmin(user._id[ObjectId])) {
      "all"
    } else {
      val process = parentProcess(activity._id[ObjectId])
      if (ProcessApi.canManage(user._id[ObjectId], process)) {
        "manage"
      } else if (action.assignee_person_id[ObjectId] == user._id[ObjectId]) {
        "contribute"
      } else {
        "none"
      }
    }
  }

  object teamAssignment {

    private def parentFields(activityOid: ObjectId): Map[String, Any] = {
      val process = parentProcess(activityOid)
      val processOid = process._id[ObjectId]
      val phase = ProcessApi.parentPhase(processOid)
      val phaseOid = phase._id[ObjectId]
      val project = PhaseApi.parentProject(phaseOid)
      Map("project_id" -> project._id[ObjectId], "phase_id" -> phaseOid, "process_id" -> processOid)
    }

    def list(activityOid: ObjectId): Seq[DynDoc] = {
      val assignments: Seq[DynDoc] = BWMongoDB3.activity_assignments.find(Map("activity_id" -> activityOid))
      if (assignments.nonEmpty) {
        assignments
      } else {
        val theActivity = activityById(activityOid)
        BWMongoDB3.activity_assignments.insertOne(parentFields(activityOid) ++
            Map("activity_id" -> activityOid, "role" -> theActivity.role[String]))
        teamAssignment.list(activityOid)
      }
    }

    def roleAdd(activityOid: ObjectId, roleName: String, optOrganizationId: Option[ObjectId]): Unit = {
      val searchRecord = Map("activity_id" -> activityOid, "role" -> roleName)
      if (BWMongoDB3.activity_assignments.count(searchRecord) == 0) {
        val baseRecord = searchRecord ++ parentFields(activityOid)
        val fullRecord: Map[String, Any] = optOrganizationId match {
          case None => baseRecord
          case Some(oid) => baseRecord ++ Map("organization_id" -> oid)
        }
        BWMongoDB3.activity_assignments.insertOne(fullRecord)
      } else {
        throw new IllegalArgumentException(s"Role '$roleName' already exists")
      }
    }

    def organizationAdd(activityOid: ObjectId, roleName: String, organizationOid: ObjectId): Unit = {
      val assignments: Seq[DynDoc] = BWMongoDB3.activity_assignments.
        find(Map("activity_id" -> activityOid, "role" -> roleName))
      assignments.length match {
        case 0 => throw new IllegalArgumentException("Severe system error!")
        case 1 => val assignment = assignments.head
          if (assignment.has("organization_id"))
            throw new IllegalArgumentException(s"Organization already assigned to this activity and role")
          val updateResult = BWMongoDB3.activity_assignments.updateOne(
            Map("activity_id" -> activityOid, "role" -> roleName),
            Map("$set" -> Map("organization_id" -> organizationOid)))
          if (updateResult.getMatchedCount == 0)
            throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
        case _ =>
          throw new IllegalArgumentException("Wrong state for adding organization")
      }
    }

    def personAdd(activityOid: ObjectId, roleName: String, organizationOid: ObjectId, personOid: ObjectId,
        individualRole: String, documentAccess: Seq[String]): Unit = {

      def samePersonAndIndividualRole(assignment: DynDoc): Boolean = {
        assignment.has("person_id") && assignment.person_id[ObjectId] == personOid &&
            assignment.has("individual_role") && assignment.individual_role[String] == individualRole
      }

      val query = Map("activity_id" -> activityOid, "role" -> roleName, "organization_id" -> organizationOid)
      val baseRecord = query ++ parentFields(activityOid)
      val assignments: Seq[DynDoc] = BWMongoDB3.activity_assignments.find(query)
      assignments.length match {
        case 0 => throw new IllegalArgumentException("Role and organization must be added first")
        case 1 => val assignment = assignments.head
          if (assignment.has("person_id")) {
            if (samePersonAndIndividualRole(assignment))
              throw new IllegalArgumentException(s"Person and individual-role already assigned to this activity, role")
            else
              BWMongoDB3.activity_assignments.insertOne(baseRecord ++ Map("person_id" -> personOid,
                "individual_role" -> individualRole, "document_access" -> documentAccess))
          } else {
            val assignmentOid = assignment._id[ObjectId]
            val updateResult = BWMongoDB3.activity_assignments.updateOne(Map("_id" -> assignmentOid),
              Map("$set" -> Map("person_id" -> personOid, "individual_role" -> individualRole,
                "document_access" -> documentAccess)))
            if (updateResult.getMatchedCount == 0)
              throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
          }
        case _ =>
          BWMongoDB3.activity_assignments.insertOne(baseRecord ++ Map("person_id" -> personOid,
            "individual_role" -> individualRole, "document_access" -> documentAccess))
      }
    }

    def deleteAssignment(assignmentOid: ObjectId): Unit = {
      val theAssignment: DynDoc = BWMongoDB3.activity_assignments.find(Map("_id" -> assignmentOid)).head
      if (RoleListSecondary.secondaryRoles.contains(theAssignment.role[String])) {
        val deleteResult = BWMongoDB3.activity_assignments.deleteOne(Map("_id" -> assignmentOid))
        if (deleteResult.getDeletedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $deleteResult")
      } else {
        val activityOid = theAssignment.activity_id[ObjectId]
        val role = theAssignment.role[String]
        val count = BWMongoDB3.activity_assignments.count(Map("activity_id" -> activityOid, "role" -> role))
        if (count > 1) {
          val deleteResult = BWMongoDB3.activity_assignments.deleteOne(Map("_id" -> assignmentOid))
          if (deleteResult.getDeletedCount == 0)
            throw new IllegalArgumentException(s"MongoDB update failed: $deleteResult")
        } else if (count == 1) {
          val updateResult = BWMongoDB3.activity_assignments.updateOne(Map("_id" -> assignmentOid),
            Map("$unset" -> Map("organization_id" -> true, "person_id" -> true, "individual_role" -> true)))
          if (updateResult.getMatchedCount == 0)
            throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
        } else
          throw new IllegalArgumentException("Severe system error")
      }
    }

  }

}
