package com.buildwhiz.obsolete

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.types.ObjectId
import org.camunda.bpm.engine.delegate.{DelegateExecution, JavaDelegate}

class EndProject extends JavaDelegate {

  def execute(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "execute()", "ENTRY", de)
    try {
      val projectOid = new ObjectId(de.getVariable("project_id").asInstanceOf[String])
      val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid),
        Map("$set" -> Map("status" -> "ended", "timestamps.end" -> System.currentTimeMillis)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      BWLogger.log(getClass.getName, "execute()", "EXIT-OK", de)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "execute()", s"ERROR ${t.getClass.getName}(${t.getMessage})", de)
    }
  }


}
