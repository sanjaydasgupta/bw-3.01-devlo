package com.buildwhiz.jelly

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.BWLogger
import org.bson.types.ObjectId
import org.camunda.bpm.engine.delegate.{DelegateExecution, JavaDelegate}

class ActivityHandlerEnd extends JavaDelegate {

  def execute(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "notify()", "ENTRY", de)
    try {
      val identity = Map("_id" -> new ObjectId(de.getVariable("activity_id").asInstanceOf[String]))
      val updateResult = BWMongoDB3.activities.updateOne(identity, Map("$set" ->
        Map("status" -> "ended", "timestamps.end" -> System.currentTimeMillis())))
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
