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

  private val rawExternalStatusMap: Map[String, String] = Map(
    "Not-Started" -> "Not-Started", "Pre-Approved" -> "Not-Started", "Deliverable-Started" -> "Active",
    "Deliverable-Started And Pre-Approved" -> "Active", "Deliverable-Completed" -> "Review",
    "Completion-Detected" -> "Completed", "Completed" -> "Completed", "Completed-pl" -> "Completed",
    "Review-Reject" -> "Rework")
  val externalStatusMapWithDefault = new WithDefault[String, String](rawExternalStatusMap, _ => "Unknown")

  def aggregateStatus(deliverables: Seq[DynDoc]): String = {
    val distinctStatusValues = deliverables.map(_.status[String]).distinct
    distinctStatusValues.length match {
      case 0 => "Unknown"
      case 1 => externalStatusMapWithDefault(distinctStatusValues.head)
      case _ => "Active"
    }
  }

  def taskStatusMap(deliverables: Seq[DynDoc]): Map[ObjectId, String] = {
    val bpmnStatusMap: Map[String, String] = Map("Not-Started" -> "Upcoming", "Completed" -> "Completed",
      "Active" -> "Current")
    val deliverablesGroupedByActivity = deliverables.groupBy(_.activity_id[ObjectId])
    val activityStatusMap = deliverablesGroupedByActivity.map(pair => (pair._1, aggregateStatus(pair._2)))
    val taskBpmnStatusMap = activityStatusMap.map(kv => (kv._1, bpmnStatusMap.getOrElse(kv._2, "Unknown")))
    val mapWithDefault = new WithDefault[ObjectId, String](taskBpmnStatusMap, _ => "Unknown")
    mapWithDefault
  }
}
