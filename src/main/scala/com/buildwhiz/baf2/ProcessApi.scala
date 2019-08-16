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

  def processesByIds(processOids: Seq[ObjectId]): Seq[DynDoc] =
    BWMongoDB3.processes.find(Map("_id" -> Map($in -> processOids)))

  def processById(processOid: ObjectId): DynDoc = BWMongoDB3.processes.find(Map("_id" -> processOid)).head

  def exists(processOid: ObjectId): Boolean = BWMongoDB3.processes.find(Map("_id" -> processOid)).nonEmpty

  def allActivityOids(process: DynDoc): Seq[ObjectId] = process.activity_ids[Many[ObjectId]]

  def allActivities(process: DynDoc): Seq[DynDoc] = {
    val activityOids = allActivityOids(process)
    ActivityApi.activitiesByIds(activityOids)
  }

  def allActivities(pOid: ObjectId): Seq[DynDoc] = allActivities(processById(pOid))

  def delete(process: DynDoc, request: HttpServletRequest): Unit = {
    val processOid = process._id[ObjectId]
    if (isActive(process))
      throw new IllegalArgumentException(s"Process '${process.name[String]}' is still active")

    BWMongoDB3.activity_assignments.deleteMany(Map("process_id" -> processOid))

    val processDeleteResult = BWMongoDB3.processes.deleteOne(Map("_id" -> processOid))
    if (processDeleteResult.getDeletedCount == 0)
      throw new IllegalArgumentException(s"MongoDB error: $processDeleteResult")

    val phaseUpdateResult = BWMongoDB3.phases.updateOne(Map("process_ids" -> processOid),
      Map("$pull" -> Map("process_ids" -> processOid)))

    val activityOids: Seq[ObjectId] = allActivities(process).map(_._id[ObjectId])
    val activityDeleteCount = if (activityOids.nonEmpty)
      BWMongoDB3.activities.deleteMany(Map("_id" -> Map("$in" -> activityOids))).getDeletedCount
    else
      0

    val message = s"Deleted process '${process.name[String]}' (${process._id[ObjectId]}). " +
      s"Also updated ${phaseUpdateResult.getModifiedCount} phase records, " +
      s"and deleted $activityDeleteCount activities"
    BWLogger.audit(getClass.getName, request.getMethod, message, request)
  }

  def parentPhase(processOid: ObjectId): DynDoc = {
    BWMongoDB3.phases.find(Map("process_ids" -> processOid)).head
  }

  def canDelete(process: DynDoc): Boolean = !isActive(process)

  def isActive(process: DynDoc): Boolean = {
    //val processName = process.name[String]
    if (process.has("process_instance_id")) {
      val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
      val query = rts.createProcessInstanceQuery()
      val instance = query.processInstanceId(process.process_instance_id[String]).active().singleResult()
      val found = instance != null
      //val message = s"Process '$processName' has 'process_instance_id' field, Active instances found: $found"
      //BWLogger.log(getClass.getName, "isActive", message)
      found
    } else {
      //val message = s"Process '$processName' has no 'process_instance_id'"
      //BWLogger.log(getClass.getName, "isActive", message)
      false
    }
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

  def hasRole(personOid: ObjectId, process: DynDoc): Boolean = {
    isAdmin(personOid, process) || allActivities(process).exists(activity => ActivityApi.hasRole(personOid, activity))
  }

  def isAdmin(personOid: ObjectId, process: DynDoc): Boolean =
    process.admin_person_id[ObjectId] == personOid

  def isManager(personOid: ObjectId, process: DynDoc): Boolean = process.assigned_roles[Many[Document]].
    exists(ar => ar.person_id[ObjectId] == personOid &&
      ar.role_name[String].matches("(?i)(?:project-|phase-|process-)?manager"))

  def canManage(personOid: ObjectId, process: DynDoc): Boolean =
      isManager(personOid, process) || isAdmin(personOid, process) ||
      PhaseApi.canManage(personOid, parentPhase(process._id[ObjectId]))

  def displayStatus(process: DynDoc): String = {
    if (isActive(process)) {
      "active"
    } else {
      val timestamps: DynDoc = process.timestamps[Document]
      if (timestamps.has("end"))
        "ended"
      else
        "dormant"
    }
  }

}
