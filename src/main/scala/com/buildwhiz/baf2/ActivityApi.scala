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

  def addChangeLogEntry(activityOid: ObjectId, description: String, userOid: Option[ObjectId] = None,
      percentComplete: Option[Int] = None): Unit = {
    if (userOid.map(PersonApi.exists).forall(_ == false))
      throw new IllegalArgumentException(s"Bad user-id: '${userOid.get}'")
    val timestamp = System.currentTimeMillis
    val changeLogEntry = (userOid, percentComplete) match {
      case (None, None) => Map("timestamp" -> timestamp, "description" -> description)
      case (Some(updaterOid), None) =>
        Map("timestamp" -> timestamp, "updater_person_id" -> updaterOid, "description" -> description)
      case (Some(updaterOid), Some(pct)) =>
        if (pct < 0 || pct > 100)
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
}
