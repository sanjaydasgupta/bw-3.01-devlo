package com.buildwhiz.jelly

import com.buildwhiz.baf2.ActivityApi
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.BWLogger
import org.bson.types.ObjectId
import org.camunda.bpm.engine.delegate.{DelegateExecution, JavaDelegate}

class ActivityHandlerEnd extends JavaDelegate {

  def execute(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "execute()", "ENTRY", de)
    try {
      val activityOid = new ObjectId(de.getVariable("activity_id").asInstanceOf[String])
      val timestamp = System.currentTimeMillis()
      val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid), Map("$set" ->
        Map("status" -> "ended", "timestamps.end" -> timestamp)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      ActivityApi.addChangeLogEntry(activityOid, s"Ended Execution")
      BWLogger.log(getClass.getName, "execute()", "EXIT-OK", de)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "execute()", s"ERROR ${t.getClass.getName}(${t.getMessage})", de)
    }
  }

}
