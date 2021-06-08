package com.buildwhiz.baf3

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import org.bson.types.ObjectId

import scala.collection.immutable.Map.WithDefault

object DeliverableApi {

  def deliverableById(deliverableOid: ObjectId): DynDoc = BWMongoDB3.deliverables.find(Map("_id" -> deliverableOid)).head

  def deliverablesByTeamOid(teamOid: ObjectId): Seq[DynDoc] =
    BWMongoDB3.deliverables.find(Map("team_assignments" -> Map($elemMatch -> Map("team_id" -> teamOid))))

  def deliverablesByTeamOids(teamOids: Seq[ObjectId]): Seq[DynDoc] = BWMongoDB3.deliverables.
      find(Map("team_assignments" -> Map($elemMatch -> Map("team_id" -> Map($in -> teamOids)))))

  def deliverablesByActivityOids(activityOids: Seq[ObjectId]): Seq[DynDoc] =
      BWMongoDB3.deliverables.find(Map("activity_id" -> Map($in -> activityOids)))

  def taskStatusMap(deliverables: Seq[DynDoc]): Map[ObjectId, String] = {
    def aggregateStatus(statusValues: Seq[String]): String =
      if (statusValues.distinct.length == 1) {
        statusValues.head
      } else {
        "Active"
      }

    val theMap = deliverables.groupBy(_.activity_id[String]).map(kv => (new ObjectId(kv._1), kv._2.map(_.status[String]))).
        map(kv => (kv._1, aggregateStatus(kv._2)))
    val mapWithDefault = new WithDefault[ObjectId, String](theMap, _ => "Unknown")

    mapWithDefault
  }
}
