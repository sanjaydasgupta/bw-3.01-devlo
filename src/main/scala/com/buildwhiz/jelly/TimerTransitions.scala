package com.buildwhiz.jelly

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.baf2.ProcessApi
import com.buildwhiz.utils.{BWLogger, BpmnUtils}
import org.bson.types.ObjectId
import org.camunda.bpm.engine.delegate.{DelegateExecution, ExecutionListener}
import org.bson.Document

class TimerTransitions extends ExecutionListener with BpmnUtils {

  def notify(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "notify()", "ENTRY", de)
    try {
      val processOid = new ObjectId(de.getVariable("process_id").asInstanceOf[String])
      val bpmnName = getBpmnName(de)
      val timerId = de.getCurrentActivityId
      val (event, status) = de.getEventName match {
        case "start" => ("start", "running")
        case "end" => ("end", "ended")
      }
      val process: DynDoc = ProcessApi.processById(processOid)
      val timers: Seq[DynDoc] = process.timers[Many[Document]]
      val timerIdx = timers.indexWhere(t => t.bpmn_id[String] == timerId && t.bpmn_name[String] == bpmnName)
      val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> processOid),
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
