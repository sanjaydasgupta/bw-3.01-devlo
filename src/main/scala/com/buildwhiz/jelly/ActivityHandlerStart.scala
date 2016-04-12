package com.buildwhiz.jelly

import java.util.{ArrayList => JArrayList}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines
import org.camunda.bpm.engine.delegate.{DelegateExecution, JavaDelegate}
import org.camunda.bpm.engine.repository.ProcessDefinition

import scala.collection.JavaConversions._

class ActivityHandlerStart extends JavaDelegate {

  private def setupEssentials(de: DelegateExecution): Unit = {
    def oneVariable(v: String): Unit = {
      if (!de.hasVariable(v)) {
        de.getSuperExecution match {
          case null =>
            val msg = s"ERROR: Failed to find SuperExecution. Searching value of '$v'"
            BWLogger.log(getClass.getName, "setupVariables()", msg, de)
            throw new IllegalArgumentException(msg)
          case superExec => superExec.hasVariable(v) match {
            case false =>
              val msg = s"ERROR: Failed to find value of '$v' in SuperExecution"
              BWLogger.log(getClass.getName, "setupVariables()", msg, de)
              throw new IllegalArgumentException(msg)
            case true => de.setVariable(v, superExec.getVariable(v))
          }
        }
      }
    }
    Seq("project_id", "phase_id").foreach(oneVariable)
  }

  private def getBpmnName(de: DelegateExecution): String = {
    val repositoryService = ProcessEngines.getDefaultProcessEngine.getRepositoryService
    val allProcessDefinitions: Seq[ProcessDefinition] =
      repositoryService.createProcessDefinitionQuery().latestVersion().list()
    allProcessDefinitions.find(_.getId == de.getProcessDefinitionId).head.getKey
  }

  def execute(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "notify()", "ENTRY", de)
    try {
      setupEssentials(de)
      val phaseOid = new ObjectId(de.getVariable("phase_id").asInstanceOf[String])
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val activityOids: Seq[ObjectId] = phase.activity_ids[ObjectIdList]
      //val activityName = de.getSuperExecution.getCurrentActivityName.replaceAll("[\\s-]+", "")
      val activityName = de.getSuperExecution.getCurrentActivityName.replaceAll("[\\s]+", " ")
      val bpmnName = getBpmnName(de.getSuperExecution)
      val activity: DynDoc = BWMongoDB3.activities.
        find(Map("_id" -> Map("$in" -> activityOids), "name" -> activityName, "bpmn_name" -> bpmnName)).head

      de.setVariable("activity_id", activity._id[ObjectId].toString)

      val actions: Seq[DynDoc] = activity.actions[DocumentList]

      val prerequisiteNames: Seq[String] = actions.filter(_.`type`[String] == "prerequisite").map(_.name[String])
      val prerequisiteNamesList = new JArrayList[String]()
      prerequisiteNamesList.addAll(prerequisiteNames)
      de.setVariable("prerequisite_action_names", prerequisiteNamesList)

      val reviewNames: Seq[String] = actions.filter(_.`type`[String] == "review").map(_.name[String])
      val reviewNamesList = new JArrayList[String]()
      reviewNamesList.addAll(reviewNames)
      de.setVariable("review_action_names", reviewNamesList)

      val mainActionName: String = actions.filter(_.`type`[String] == "main").map(_.name[String]).head
      de.setVariable("action_name", mainActionName)

      val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activity._id[ObjectId]), Map("$set" ->
        Map("status" -> "running", "timestamps.start" -> System.currentTimeMillis())))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB error: $updateResult")

      BWLogger.log(getClass.getName, "notify()", "EXIT-OK", de)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "execute()", s"ERROR ${t.getClass.getName}(${t.getMessage})", de)
    }
  }

}
