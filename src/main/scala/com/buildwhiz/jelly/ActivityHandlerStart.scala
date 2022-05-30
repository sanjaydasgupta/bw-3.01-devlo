package com.buildwhiz.jelly

import java.util.{ArrayList => JArrayList}

import com.buildwhiz.baf2.{ActivityApi, ProcessApi}
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, BpmnUtils}
import org.bson.types.ObjectId
import org.camunda.bpm.engine.delegate.{DelegateExecution, JavaDelegate}

import scala.jdk.CollectionConverters._

class ActivityHandlerStart extends JavaDelegate with BpmnUtils {

  private def setupEssentials(de: DelegateExecution): Unit = {
    def oneVariable(v: String): Unit = {
      if (!de.hasVariable(v)) {
        de.getSuperExecution match {
          case null =>
            val msg = s"ERROR: Failed to find SuperExecution. Searching value of '$v'"
            BWLogger.log(getClass.getName, "setupVariables()", msg, de)
            throw new IllegalArgumentException(msg)
          case superExec =>
            if (superExec.hasVariable(v)) {
              de.setVariable(v, superExec.getVariable(v))
            } else {
              val msg = s"ERROR: Failed to find value of '$v' in SuperExecution"
              BWLogger.log(getClass.getName, "setupVariables()", msg, de)
              throw new IllegalArgumentException(msg)
            }
          }
        }
      }
    Seq("project_id", "process_id").foreach(oneVariable)
  }

  def execute(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "notify()", "ENTRY", de)
    try {
      setupEssentials(de)
      val processOid = new ObjectId(de.getVariable("process_id").asInstanceOf[String])
      val process: DynDoc = ProcessApi.processById(processOid)
      val activityOids: Seq[ObjectId] = ProcessApi.allActivities(Right(process)).map(_._id[ObjectId])
      val activityName = de.getCurrentActivityName.replaceAll("[\\s]+", " ")
      val bpmnName = getBpmnName(de)
      val query = Map("_id" -> Map("$in" -> activityOids), "name" -> activityName, "bpmn_name" -> bpmnName)
      val activity: DynDoc = BWMongoDB3.tasks.find(query).headOption match {
        case Some(a) => a
        case None => throw new IllegalArgumentException(s"Query did not match any activity: '$query'")
      }

      de.setVariable("activity_id", activity._id[ObjectId].toString)

      val actions: Seq[DynDoc] = ActivityApi.allActions(activity)

      val prerequisiteNames: Seq[String] = actions.filter(_.`type`[String] == "prerequisite").map(_.name[String])
      val prerequisiteNamesList = new JArrayList[String]()
      prerequisiteNamesList.addAll(prerequisiteNames.asJava)
      de.setVariable("prerequisite_action_names", prerequisiteNamesList)

      val reviewNames: Seq[String] = actions.filter(_.`type`[String] == "review").map(_.name[String])
      val reviewNamesList = new JArrayList[String]()
      reviewNamesList.addAll(reviewNames.asJava)
      de.setVariable("review_action_names", reviewNamesList)

      val mainActionName: String = actions.filter(_.`type`[String] == "main").map(_.name[String]).head
      de.setVariable("action_name", mainActionName)

      val timestamp = System.currentTimeMillis()
      val updateResult = BWMongoDB3.tasks.updateOne(Map("_id" -> activity._id[ObjectId]),
          Map("$set" -> Map("status" -> "running", "timestamps.start" -> timestamp,
          "activity_instance_id" -> de.getActivityInstanceId)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB error: $updateResult")

      ActivityApi.startedByBpmnEngine(activity._id[ObjectId])

      BWLogger.log(getClass.getName, "notify()", "EXIT-OK", de)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "execute()", s"ERROR ${t.getClass.getName}(${t.getMessage})", de)
    }
  }

}
