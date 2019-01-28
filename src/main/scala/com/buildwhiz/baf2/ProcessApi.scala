package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.BWLogger
import javax.servlet.http.HttpServletRequest
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines

object ProcessApi {

  def allActivityOids(process: DynDoc): Seq[ObjectId] = process.activity_ids[Many[ObjectId]]
  def allActivities(process: DynDoc): Seq[DynDoc] = {
    val activityOids = allActivityOids(process)
    BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityOids)))
  }

  def delete(process: DynDoc, request: HttpServletRequest): Unit = {
    val processOid = process._id[ObjectId]
    if (isActive(process))
      throw new IllegalArgumentException(s"Process '$processOid' is still active")
    val activityOids = allActivities(process)
    val activityDeleteResult = BWMongoDB3.activities.deleteMany(Map("_id" -> Map("$in" -> activityOids)))
    if (activityDeleteResult.getDeletedCount == 0)
      throw new IllegalArgumentException(s"MongoDB error: $activityDeleteResult")
    val deleteMessage = s"Deleted ${activityDeleteResult.getDeletedCount} activities"
    BWLogger.audit(getClass.getName, request.getMethod, deleteMessage, request)
    val processDeleteResult = BWMongoDB3.processes.deleteOne(Map("_id" -> processOid))
    if (processDeleteResult.getDeletedCount == 0)
      throw new IllegalArgumentException(s"MongoDB error: $processDeleteResult")
    val phaseUpdateResult = BWMongoDB3.phases.updateOne(Map("process_ids" -> processOid), Map("$pull" -> Map("process_ids" -> processOid)))
    if (phaseUpdateResult.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB error: $phaseUpdateResult")
    val message = s"Deleted process '${process.name[String]}' (${process._id[ObjectId]})"
    BWLogger.audit(getClass.getName, request.getMethod, message, request)
  }

  def parentPhase(process: DynDoc): DynDoc = {
    val processOid = process._id[ObjectId]
    BWMongoDB3.phases.find(Map("process_ids" -> processOid)).head
  }

  def canDelete(process: DynDoc): Boolean = !isActive(process)

  def isActive(process: DynDoc): Boolean = {
    if (process.has("process_instance_id")) {
      val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
      try {
        rts.getVariables(process.process_instance_id[String])
        true
      } catch {
        case _: AnyRef => false
      }
    } else
      false
  }

  def isHealthy(process: DynDoc): Boolean = {
    if (process.status[String] == "running") {
      val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
      try {
        rts.getVariables(process.process_instance_id[String])
        true
      } catch {
        case _: AnyRef => false
      }
    } else
      true
  }

  def processProcess(process: DynDoc, project: DynDoc, personOid: ObjectId): DynDoc = {
    val activities: Seq[DynDoc] = BWMongoDB3.activities.
      find(Map("_id" -> Map("$in" -> process.activity_ids[Many[ObjectId]])))
    val isRelevant = activities.flatMap(_.actions[Many[Document]]).
      exists(_.assignee_person_id[ObjectId] == personOid)
    process.can_launch = process.status[String] == "defined" && project.status[String] == "running"
    process.is_managed = process.admin_person_id[ObjectId] == personOid
    process.is_relevant = isRelevant || process.is_managed[Boolean]
    val actions: Seq[DynDoc] = activities.flatMap(_.actions[Many[Document]])
    if (actions.exists(action => action.status[String] == "waiting" && action.assignee_person_id[ObjectId] == personOid))
      process.display_status = "waiting"
    else if (actions.exists(action => action.status[String] == "waiting"))
      process.display_status = "waiting2"
    else
      process.display_status = process.status[String]
    val subBpmns: Seq[DynDoc] = process.bpmn_timestamps[Many[Document]].filter(_.parent_name[String] != "")
    process.sub_bpmns = subBpmns.sortBy(_.name[String]).map(_.asDoc)
    process.healthy = isHealthy(process)
    process.docsUrl = s"docs?process_id=${process._id[ObjectId]}"
    process.remove("activity_ids")
    process
  }

}
