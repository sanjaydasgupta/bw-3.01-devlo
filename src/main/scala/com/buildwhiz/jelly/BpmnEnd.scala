package com.buildwhiz.jelly

import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import BWMongoDB3._
import org.camunda.bpm.engine.delegate.{DelegateExecution, JavaDelegate}
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines
import org.camunda.bpm.engine.repository.ProcessDefinition

import scala.collection.JavaConversions._

class BpmnEnd extends JavaDelegate {

  private def getBpmnName(de: DelegateExecution): String = {
    val repositoryService = ProcessEngines.getDefaultProcessEngine.getRepositoryService
    val allProcessDefinitions: Seq[ProcessDefinition] =
      repositoryService.createProcessDefinitionQuery().latestVersion().list()
    allProcessDefinitions.find(_.getId == de.getProcessDefinitionId).head.getKey
  }

  def execute(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "execute()", "ENTRY", de)
    try {
      val phaseOid = new ObjectId(de.getVariable("phase_id").asInstanceOf[String])
      val bpmnName = getBpmnName(de)
      if (de.hasVariable("top_level_bpmn") && de.getVariable("top_level_bpmn") == bpmnName) {
        val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
          Map("$set" -> Map("status" -> "ended", "timestamps.end" -> System.currentTimeMillis),
            "$push" -> Map("bpmn_timestamps" -> Map("name" -> bpmnName, "event" -> "end",
              "timestamp" -> System.currentTimeMillis))))
        if (updateResult.getModifiedCount == 0)
          throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      } else {
        val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
          Map("$push" -> Map("bpmn_timestamps" -> Map("name" -> bpmnName, "event" -> "end",
            "timestamp" -> System.currentTimeMillis))))
        if (updateResult.getModifiedCount == 0)
          throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      }
      BWLogger.log(getClass.getName, "execute()", "EXIT-OK", de)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "execute()", s"ERROR ${t.getClass.getName}(${t.getMessage})", de)
    }
  }


}
