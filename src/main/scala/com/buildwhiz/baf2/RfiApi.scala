package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import org.bson.Document
import org.bson.types.ObjectId

object RfiApi {

  def rfiById(rfiOid: ObjectId): DynDoc = BWMongoDB3.rfi_messages.find(Map("_id" -> rfiOid)).headOption match {
    case None => throw new IllegalArgumentException(s"Bad rfi-id: '$rfiOid'")
    case Some(rfi) => rfi
  }

  def exists(rfiOid: ObjectId): Boolean = BWMongoDB3.rfi_messages.find(Map("_id" -> rfiOid)).nonEmpty

  def rfisByUser(userOid: ObjectId): Seq[DynDoc] = {
    val rfis: Seq[DynDoc] = BWMongoDB3.tasks.find()
    rfis
  }

  //def hasRole(personOid: ObjectId, activity: DynDoc): Boolean =
  //  allActions(activity).exists(_.assignee_person_id[ObjectId] == personOid)

}
