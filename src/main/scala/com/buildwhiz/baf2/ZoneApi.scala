package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import org.bson.types.ObjectId

object ZoneApi {

  def zoneById(zoneOid: ObjectId): DynDoc =
      BWMongoDB3.zones.find(Map("_id" -> zoneOid)).head

  def exists(zoneOid: ObjectId): Boolean = BWMongoDB3.zones.find(Map("_id" -> zoneOid)).nonEmpty

  def canManage(personOid: ObjectId, zone: DynDoc): Boolean = {
    val phaseOid = zone.phase_id[ObjectId]
    PhaseApi.canManage(personOid, PhaseApi.phaseById(phaseOid))
  }

  def fetch(oid: Option[ObjectId] = None, name: Option[String] = None): Seq[DynDoc] =
      (oid, name) match {
    case (Some(zoneOid), _) => BWMongoDB3.zones.find(Map("_id" -> zoneOid))
    case (None, Some(zoneName)) => BWMongoDB3.zones.find(Map("name" -> zoneName))
    case _ => Seq.empty[DynDoc]
  }

  def list(optProjectOid: Option[ObjectId] = None, optPhaseOid: Option[ObjectId] = None,
      optActivityOid: Option[ObjectId] = None): Seq[DynDoc] =
    (optProjectOid, optPhaseOid, optActivityOid) match {
      case (_, _, Some(activityOid)) => BWMongoDB3.zones.find(Map("activity_ids" -> activityOid))
      case (_, Some(phaseOid), _) => BWMongoDB3.zones.find(Map("phase_id" -> phaseOid))
      case (Some(projectOid), _, _) => BWMongoDB3.zones.find(Map("project_id" -> projectOid))
      case _ => Seq.empty[DynDoc]
    }

  def allActivities(zone: DynDoc): Seq[DynDoc] = {
    val activityOids = zone.activity_ids[Many[ObjectId]]
    BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityOids)))
  }

}
