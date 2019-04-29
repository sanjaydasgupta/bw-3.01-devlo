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

  object teamAssgnment {
    def staffAssignmentList(activityOid: ObjectId): Seq[DynDoc] = {
      val assignments: Seq[DynDoc] = BWMongoDB3.activity_assignments.find(Map("activity_id" -> activityOid))
      if (assignments.nonEmpty) {
        assignments
      } else {
        val theActivity = activityById(activityOid)
        BWMongoDB3.activity_assignments.insertOne(Map("activity_id" -> activityOid, "role" -> theActivity.role[String]))
        teamAssgnment.staffAssignmentList(activityOid)
      }
    }

    def staffAssignmentRoleAdd(activityOid: ObjectId, roleName: String, optOrganizationId: Option[ObjectId]): Unit = {
      val baseRecord = Map("activity_id" -> activityOid, "role" -> roleName)
      if (BWMongoDB3.activity_assignments.count(baseRecord) == 0) {
        val fullRecord: Map[String, Any] = optOrganizationId match {
          case None => baseRecord
          case Some(oid) => baseRecord ++ Map("organization_id" -> oid)
        }
        BWMongoDB3.activity_assignments.insertOne(fullRecord)
      } else {
        throw new IllegalArgumentException(s"Role '$roleName' already exists")
      }
    }
  }

}
