package com.buildwhiz.jelly

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, BpmnUtils, DateTimeUtils}
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.engine.delegate.{DelegateExecution, ExecutionListener}

class BpmnStart extends ExecutionListener with BpmnUtils with DateTimeUtils {

  private def duration2iso(duration: String): String = {
    val Array(days, hours, minutes) = duration.split(":").map(_.toInt)
    s"P${days}DT${hours}H${minutes}M"
  }

  private def setupEssentials(de: DelegateExecution): Unit = {
    def oneVariable(v: String): Unit = {
      if (!de.hasVariable(v)) {
        de.getSuperExecution match {
          case null =>
            val msg = s"ERROR: Failed to find SuperExecution. Searching value of '$v'"
            BWLogger.log(getClass.getName, "setupVariables()", msg, de)
            throw new IllegalArgumentException(msg)
          case superExec => if (superExec.hasVariable(v)) {
              de.setVariable(v, superExec.getVariable(v))
            } else {
              val msg = s"ERROR: Failed to find value of '$v' in SuperExecution"
              BWLogger.log(getClass.getName, "setupVariables()", msg, de)
              throw new IllegalArgumentException(msg)
            }
        }
      }
    }
    Seq("project_id", "phase_id").foreach(oneVariable)
  }

  def notify(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "execute()", "ENTRY", de)
    try {
      setupEssentials(de)
      val phaseOid = new ObjectId(de.getVariable("phase_id").asInstanceOf[String])
      val thePhase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val bpmnName = getBpmnName(de)
      if (de.hasVariable("top_level_bpmn") && de.getVariable("top_level_bpmn") == bpmnName) {
        val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
          Map("$set" -> Map("status" -> "running", "timestamps.start" -> System.currentTimeMillis),
            "$push" -> Map("bpmn_timestamps" -> Map("name" -> bpmnName, "parent_name" -> "",
              "status" -> "running", "timestamps" -> Map("start" -> System.currentTimeMillis)))))
        if (updateResult.getModifiedCount == 0)
          throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      } else {
        val callerBpmnName = getBpmnName(de.getSuperExecution)
        val bpmnTimestamps: Seq[DynDoc] = thePhase.bpmn_timestamps[Many[Document]]
        val idx = bpmnTimestamps.indexWhere(ts => ts.name[String] == bpmnName &&
          ts.parent_name[String] == callerBpmnName)
        val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> thePhase._id[ObjectId]), Map("$set" ->
          Map(s"bpmn_timestamps.$idx.status" -> "running",
          s"bpmn_timestamps.$idx.timestamps.start" -> System.currentTimeMillis)))
        if (updateResult.getModifiedCount == 0)
          throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      }
      if (thePhase has "timers") {
        val timers: Seq[DynDoc] = thePhase.timers[Many[Document]].filter(_.bpmn_name[String] == bpmnName)
        timers.foreach(t => de.setVariable(t.variable[String], duration2iso(t.duration[String])))
      }
      if (thePhase has "variables") {
        val variables: Seq[DynDoc] = thePhase.variables[Many[Document]].filter(_.bpmn_name[String] == bpmnName)
        variables.foreach(t => de.setVariable(t.name[String], t.value[Any]))
      }
      BWLogger.log(getClass.getName, "execute()", "EXIT-OK", de)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "execute()", s"ERROR ${t.getClass.getName}(${t.getMessage})", de)
    }
  }

}
