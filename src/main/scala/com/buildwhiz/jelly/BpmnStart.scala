package com.buildwhiz.jelly

import com.buildwhiz.BpmnUtils
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.types.ObjectId
import org.camunda.bpm.engine.delegate.{DelegateExecution, JavaDelegate}

import scala.collection.JavaConverters._

class BpmnStart extends JavaDelegate with BpmnUtils {

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

  def execute(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "execute()", "ENTRY", de)
    try {
      setupEssentials(de)
      val phaseOid = new ObjectId(de.getVariable("phase_id").asInstanceOf[String])
      val bpmnName = getBpmnName(de)
      if (de.hasVariable("top_level_bpmn") && de.getVariable("top_level_bpmn") == bpmnName) {
        val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
          Map("$set" -> Map("status" -> "running", "timestamps.start" -> System.currentTimeMillis),
            "$push" -> Map("bpmn_timestamps" -> Map("name" -> bpmnName, "event" -> "start",
              "timestamp" -> System.currentTimeMillis))))
        if (updateResult.getModifiedCount == 0)
          throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      } else {
        val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
          Map("$push" -> Map("bpmn_timestamps" -> Map("name" -> bpmnName, "event" -> "start",
            "timestamp" -> System.currentTimeMillis))))
        if (updateResult.getModifiedCount == 0)
          throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      }
      val thePhase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).asScala.head
      if (thePhase has "timers") {
        val timers: Seq[DynDoc] = thePhase.timers[DocumentList].filter(_.bpmn_name[String] == bpmnName)
        timers.foreach(t => de.setVariable(t.variable[String], duration2iso(t.duration[String])))
      }
      if (thePhase has "variables") {
        val variables: Seq[DynDoc] = thePhase.variables[DocumentList].filter(_.bpmn_name[String] == bpmnName)
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
