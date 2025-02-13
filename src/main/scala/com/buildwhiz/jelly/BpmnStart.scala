package com.buildwhiz.jelly

import com.buildwhiz.baf2.ProcessApi
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, BpmnUtils, DateTimeUtils}
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.engine.delegate.{DelegateExecution, ExecutionListener}

class BpmnStart extends ExecutionListener with BpmnUtils with DateTimeUtils {

  private def setupEssentials(de: DelegateExecution): Unit = {
    def oneVariable(v: String): Unit = {
      if (!de.hasVariable(v)) {
        de.getSuperExecution match {
          case null =>
            val msg = s"ERROR: Failed to find SuperExecution. Searching value of '$v'"
            BWLogger.log(getClass.getName, "setupEssentials()", msg, de)
            throw new IllegalArgumentException(msg)
          case superExec => if (superExec.hasVariable(v)) {
              de.setVariable(v, superExec.getVariable(v))
            } else {
              val msg = s"ERROR: Failed to find value of '$v' in SuperExecution"
              BWLogger.log(getClass.getName, "setupEssentials()", msg, de)
              throw new IllegalArgumentException(msg)
            }
        }
      }
    }
    Seq("project_id", "process_id").foreach(oneVariable)
  }

  def notify(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "notify()", "ENTRY", de)
    try {
      setupEssentials(de)
      val processInstanceId = de.getProcessInstanceId
      val processOid = new ObjectId(de.getVariable("process_id").asInstanceOf[String])
      val theProcess: DynDoc = ProcessApi.processById(processOid)
      val bpmnName = getBpmnName(de)
      val msNow = System.currentTimeMillis
      if (de.hasVariable("top_level_bpmn") && de.getVariable("top_level_bpmn") == bpmnName) {
        val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> processOid),
          Map("$set" -> Map("status" -> "running", "timestamps.start" -> msNow),
            "$push" -> Map("bpmn_timestamps" -> Map("name" -> bpmnName, "parent_name" -> "",
            "status" -> "running", "timestamps" -> Map("start" -> msNow),
            "process_instance_id" -> processInstanceId))))
        if (updateResult.getModifiedCount == 0)
          throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      } else {
        val callerBpmnName = getBpmnName(de.getSuperExecution)
        val bpmnTimestamps: Seq[DynDoc] = theProcess.bpmn_timestamps[Many[Document]]
        val idx = bpmnTimestamps.indexWhere(ts => ts.name[String] == bpmnName &&
          ts.parent_name[String] == callerBpmnName)
        val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> theProcess._id[ObjectId]), Map("$set" ->
          Map(s"bpmn_timestamps.$idx.status" -> "running", s"bpmn_timestamps.$idx.timestamps.start" -> msNow,
            s"bpmn_timestamps.$idx.process_instance_id" -> processInstanceId)))
        if (updateResult.getModifiedCount == 0)
          throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      }
      if (theProcess has "timers") {
        val timers: Seq[DynDoc] = theProcess.timers[Many[Document]].filter(_.bpmn_name[String] == bpmnName)
        timers.foreach(t => de.setVariable(t.variable[String], duration2iso(t.duration[String])))
      }
      if (theProcess has "variables") {
        val variables: Seq[DynDoc] = theProcess.variables[Many[Document]].filter(_.bpmn_name[String] == bpmnName)
        variables.foreach(t => de.setVariable(t.name[String], t.value[Any]))
      }
      BWLogger.log(getClass.getName, "notify()", "EXIT-OK", de)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "notify()", s"ERROR ${t.getClass.getName}(${t.getMessage})", de)
    }
  }

}
