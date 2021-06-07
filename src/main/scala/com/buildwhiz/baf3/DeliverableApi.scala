package com.buildwhiz.baf3

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._

import org.bson.types.ObjectId

object DeliverableApi {

  def deliverableById(deliverableOid: ObjectId): DynDoc = BWMongoDB3.deliverables.find(Map("_id" -> deliverableOid)).head

  def deliverablesByTeamOid(teamOid: ObjectId): Seq[DynDoc] =
    BWMongoDB3.deliverables.find(Map("team_assignments" -> Map($elemMatch -> Map("team_id" -> teamOid))))

  def deliverablesByTeamOids(teamOids: Seq[ObjectId]): Seq[DynDoc] = BWMongoDB3.deliverables.
    find(Map("team_assignments" -> Map($elemMatch -> Map("team_id" -> Map($in -> teamOids)))))

}
