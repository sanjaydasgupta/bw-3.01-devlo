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

  val externalStatusMap: Map[String, String] = Map(
    "Not-Started" -> "Not-Started", "Pre-Approved" -> "Not-Started", "Deliverable-Started" -> "Active",
    "Deliverable-Started And Pre-Approved" -> "Active", "Deliverable-Completed" -> "Review",
    "Completion-Detected" -> "Completed", "Completed" -> "Completed", "Completed-pl" -> "Completed",
    "Review-Reject" -> "Rework")

  def aggregateStatus(statusValues: Seq[String]): String =
    if (statusValues.distinct.length == 1) {
      statusValues.head
    } else {
      "Active"
    }

  def taskStatusMap(deliverables: Seq[DynDoc]): Map[ObjectId, String] = {
    val bpmnStatusMap: Map[String, String] = Map("Not-Started" -> "Upcoming", "Completed" -> "Completed",
      "Active" -> "Current")
    val theGroups = deliverables.groupBy(_.activity_id[ObjectId])
    val statusValues = theGroups.map(kv =>
      (kv._1, kv._2.map(deliverable => externalStatusMap.getOrElse(deliverable.status[String], "Unknown"))))
    val theMap = statusValues.map(kv => (kv._1, aggregateStatus(kv._2)))
    val taskBpmnStatusMap = theMap.map(kv => (kv._1, bpmnStatusMap.getOrElse(kv._2, "Unknown")))
    val mapWithDefault = new WithDefault[ObjectId, String](taskBpmnStatusMap, _ => "Unknown")

    mapWithDefault
  }
}
