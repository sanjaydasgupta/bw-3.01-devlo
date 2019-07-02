package com.buildwhiz.jelly

import com.buildwhiz.baf2.ActivityApi
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.BWLogger
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines
import org.camunda.bpm.engine.delegate.{DelegateExecution, JavaDelegate}
import org.camunda.bpm.engine.task.Task

class ActivityHandlerEnd extends JavaDelegate {

  def execute(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "execute()", "ENTRY", de)
    try {
      val activityOid = new ObjectId(de.getVariable("activity_id").asInstanceOf[String])
      ActivityHandlerEnd.end(activityOid)
      BWLogger.log(getClass.getName, "execute()", "EXIT-OK", de)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "execute()", s"ERROR ${t.getClass.getName}(${t.getMessage})", de)
    }
  }

}

object ActivityHandlerEnd {

  def end(activityOid: ObjectId, signal: Boolean = false): Unit = {
    if (signal) {
      ActivityApi.allActions(activityOid).find(_.`type`[String] == "main") match {
        case Some(main) =>
          if (main.has("camunda_execution_id")) {
            val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
            rts.messageEventReceived("Action-Complete", main.camunda_execution_id[String])
          } else {
            val theActivity = ActivityApi.activityById(activityOid)
            if (theActivity.has("activity_instance_id")) {
              val taskService = ProcessEngines.getDefaultProcessEngine.getTaskService
              val activityInstanceIdIn = theActivity.activity_instance_id[String]
              val tasks: Seq[Task] = taskService.createTaskQuery().activityInstanceIdIn(activityInstanceIdIn).list()
              if (tasks.isEmpty)
                throw new IllegalArgumentException(s"No task with activityInstanceIdIn: $activityInstanceIdIn")
              taskService.complete(tasks.head.getId)
            }
          }
        case None =>
          throw new IllegalArgumentException(s"Unable to find 'main' action activity_id: $activityOid")
      }
    }
    val timestamp = System.currentTimeMillis()
    val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid), Map("$set" ->
      Map("status" -> "ended", "timestamps.end" -> timestamp)))
    if (updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB error: $updateResult")
    ActivityApi.addChangeLogEntry(activityOid, s"Ended Execution")
  }

}
