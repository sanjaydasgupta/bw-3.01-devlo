package com.buildwhiz.obsolete

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines
import org.camunda.bpm.engine.delegate.{DelegateExecution, JavaDelegate}

import scala.collection.JavaConverters._

class LaunchPhaseProcess extends JavaDelegate {

//  private def duration2iso(duration: String): String = {
//    val Array(days, hours, minutes) = duration.split(":")
//    s"P${days}DT${hours}H${minutes}M"
//  }
//
  def execute(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "execute()", "ENTRY", de)
    try {
      val projectId = de.getVariable("project_id").asInstanceOf[String]
      val phaseId = de.getVariable("phase_id").asInstanceOf[String]
      val thePhase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> new ObjectId(phaseId))).asScala.head
      val bpmnName = thePhase.bpmn_name[String]
      val processVariables = Map("project_id" -> projectId, "phase_id" -> phaseId) //++
      val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
      rts.startProcessInstanceByKey(bpmnName, processVariables)
      val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> new ObjectId(phaseId)),
        Map("$set" -> Map("status" -> "running", "timestamps.start" -> System.currentTimeMillis)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      BWLogger.log(getClass.getName, "execute()", "EXIT-OK", de)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "execute()", s"ERROR ${t.getClass.getName}(${t.getMessage})", de)
    }
  }
}
