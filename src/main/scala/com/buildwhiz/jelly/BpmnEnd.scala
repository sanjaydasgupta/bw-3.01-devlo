package com.buildwhiz.jelly

import com.buildwhiz.baf2.ProcessApi
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, BpmnUtils}
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.engine.delegate.{DelegateExecution, ExecutionListener}

class BpmnEnd extends ExecutionListener with BpmnUtils {

  def notify(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "execute()", "ENTRY", de)
    try {
      val processOid = new ObjectId(de.getVariable("process_id").asInstanceOf[String])
      val theProcess: DynDoc = ProcessApi.processById(processOid)
      val bpmnTimestamps: Seq[DynDoc] = theProcess.bpmn_timestamps[Many[Document]]
      val bpmnName = getBpmnName(de)
      if (de.hasVariable("top_level_bpmn") && de.getVariable("top_level_bpmn") == bpmnName) {
        val idx = bpmnTimestamps.indexWhere(ts => ts.name[String] == bpmnName && ts.parent_name[String] == "")
        val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> processOid),
          Map("$set" -> Map("status" -> "ended", "timestamps.end" -> System.currentTimeMillis,
          s"bpmn_timestamps.$idx.status" -> "ended",
          s"bpmn_timestamps.$idx.timestamps.end" -> System.currentTimeMillis)))
        if (updateResult.getModifiedCount == 0)
          throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      } else {
        val callerBpmnName = getBpmnName(de.getSuperExecution)
        val idx = bpmnTimestamps.indexWhere(ts => ts.name[String] == bpmnName &&
          ts.parent_name[String] == callerBpmnName)
        val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> theProcess._id[ObjectId]), Map("$set" ->
          Map(s"bpmn_timestamps.$idx.status" -> "ended",
          s"bpmn_timestamps.$idx.timestamps.end" -> System.currentTimeMillis)))
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
