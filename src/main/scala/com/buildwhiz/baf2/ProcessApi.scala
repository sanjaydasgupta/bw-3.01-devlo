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

  def processById(processOid: ObjectId): DynDoc = BWMongoDB3.processes.find(Map("_id" -> processOid)).head

  def exists(processOid: ObjectId): Boolean = BWMongoDB3.processes.find(Map("_id" -> processOid)).nonEmpty

  def allActivityOids(process: DynDoc): Seq[ObjectId] = process.activity_ids[Many[ObjectId]]

  def allActivities(process: DynDoc): Seq[DynDoc] = {
    val activityOids = allActivityOids(process)
    BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityOids)))
  }

  def allActivities(pOid: ObjectId): Seq[DynDoc] = allActivities(processById(pOid))

  def delete(process: DynDoc, request: HttpServletRequest): Unit = {
    val processOid = process._id[ObjectId]
    if (isActive(process))
      throw new IllegalArgumentException(s"Process '$processOid' is still active")

    val phaseUpdateResult = BWMongoDB3.phases.updateOne(Map("process_ids" -> processOid),
      Map("$pull" -> Map("process_ids" -> processOid)))

    val processDeleteResult = BWMongoDB3.processes.deleteOne(Map("_id" -> processOid))
    if (processDeleteResult.getDeletedCount == 0)
      throw new IllegalArgumentException(s"MongoDB error: $processDeleteResult")

    val activityOids: Seq[ObjectId] = allActivities(process).map(_._id[ObjectId])
    val activityDeleteResult = BWMongoDB3.activities.deleteMany(Map("_id" -> Map("$in" -> activityOids)))

    val message = s"Deleted process '${process.name[String]}' (${process._id[ObjectId]}). " +
      s"Also updated ${phaseUpdateResult.getModifiedCount} phase records, " +
      s"and deleted ${activityDeleteResult.getDeletedCount} activities"
    BWLogger.audit(getClass.getName, request.getMethod, message, request)
  }

  def parentPhase(processOid: ObjectId): DynDoc = {
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

  def isHealthy(process: DynDoc): Boolean = (process.status[String] == "running") == isActive(process)

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
