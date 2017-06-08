package com.buildwhiz.jelly

import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, BpmnUtils}
import org.bson.types.ObjectId
import org.camunda.bpm.engine.delegate.{DelegateExecution, ExecutionListener}
import org.bson.Document

class TimerTransitions extends ExecutionListener with BpmnUtils {

  def notify(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "notify()", "ENTRY", de)
    try {
      val phaseOid = new ObjectId(de.getVariable("phase_id").asInstanceOf[String])
      val bpmnName = getBpmnName(de)
      val timerId = de.getCurrentActivityId
      val (event, status) = de.getEventName match {
        case "start" => ("start", "running")
        case "end" => ("end", "ended")
      }
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val timers: Seq[DynDoc] = phase.timers[Many[Document]]
      val timerIdx = timers.indexWhere(t => t.bpmn_id[String] == timerId && t.bpmn_name[String] == bpmnName)
      val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
          Map("$set" -> Map(s"timers.$timerIdx.status" -> status),
          "$addToSet" -> Map(s"timers.$timerIdx.timestamps" -> Map(event -> System.currentTimeMillis))))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      BWLogger.log(getClass.getName, "notify()", "EXIT-OK", de)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "notify()", s"ERROR ${t.getClass.getName}(${t.getMessage})", de)
    }
  }


}
