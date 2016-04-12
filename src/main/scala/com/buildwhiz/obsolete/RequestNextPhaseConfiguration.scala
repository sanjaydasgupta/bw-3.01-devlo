package com.buildwhiz.obsolete

import com.buildwhiz.infra.BWLogger
import org.camunda.bpm.engine.delegate.{DelegateExecution, JavaDelegate}

class RequestNextPhaseConfiguration extends JavaDelegate {

  def execute(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "execute()", "ENTRY", de)
    try {
//      BWMongoDB3.phases.find(Map("status" -> "defined")).headOption match {
//        case Some(phase) =>
//          val updateResult = BWMongoDB3.phases.updateOne(phase, Map("$set" -> Map("status" -> "waiting")))
//          if (updateResult.getModifiedCount == 0)
//            throw new IllegalArgumentException(s"MongoDB error: $updateResult")
//        case None =>
//      }
      BWLogger.log(getClass.getName, "execute()", "EXIT-OK", de)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "execute()", s"ERROR ${t.getClass.getName}(${t.getMessage})", de)
        throw t
    }
  }

}
