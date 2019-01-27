package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines

object ProcessApi {

  def healthy(phase: DynDoc): Boolean = {
    if (phase.status[String] == "running") {
      val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
      try {
        rts.getVariables(phase.process_instance_id[String])
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
    process.healthy = healthy(process)
    process.docsUrl = s"docs?phase_id=${process._id[ObjectId]}"
    process.remove("activity_ids")
    process
  }

}
