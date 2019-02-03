package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import org.bson.Document
import org.bson.types.ObjectId

object ActivityApi {

  def activityById(activityOid: ObjectId): DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).head

  def exists(activityOid: ObjectId): Boolean = BWMongoDB3.activities.find(Map("_id" -> activityOid)).nonEmpty

  def allActions(activity: DynDoc): Seq[DynDoc] = activity.actions[Many[Document]]

  def allActions(activityOid: ObjectId): Seq[DynDoc] = allActions(activityById(activityOid))

  def parentProcess(activityOid: ObjectId): DynDoc = {
    BWMongoDB3.processes.find(Map("activity_ids" -> activityOid)).head
  }

  def hasRole(personOid: ObjectId, activity: DynDoc): Boolean =
    allActions(activity).exists(_.assignee_person_id[ObjectId] == personOid)

}
