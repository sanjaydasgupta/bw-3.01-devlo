package com.buildwhiz.api

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import org.bson.Document
import org.bson.types.ObjectId

object Activity {

  def allActions(activity: DynDoc): Seq[DynDoc] = activity.actions[Many[Document]]

  def allActions(activityOid: ObjectId): Seq[DynDoc] = {
    val activity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).head
    allActions(activity)
  }

  def activityUsers(activity: DynDoc): Seq[ObjectId] =
      allActions(activity).flatMap(Action.actionUsers).distinct

}
