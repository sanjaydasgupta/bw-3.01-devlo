package com.buildwhiz.jelly

import com.buildwhiz.infra.BWLogger
import org.camunda.bpm.engine.delegate.{JavaDelegate, DelegateExecution}

class StartMainAction extends JavaDelegate {

  def execute(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "execute()", "ENTRY", de)
    try {
//      val query = Map("_id" -> new ObjectId(de.getVariable("activity_id").asInstanceOf[String]))
//      val activity = BWMongoDB3.activities.find(query).head
//      val actions = activity("actions").asInstanceOf[Documents]
//      val actionsWithIndex = actions.zipWithIndex
//      val actionName = de.getVariable("action_name").asInstanceOf[String]
//      actionsWithIndex.find(_._1("name") == actionName).map(_._2) match {
//        case Some(idx) =>
//          val updateResult = BWMongoDB3.activities.updateOne(query,
//            Map("$set" -> Map(s"actions.$idx.status" -> "running",
//              s"actions.$idx.timestamps.start" -> System.currentTimeMillis)))
//          if (updateResult.getModifiedCount == 0)
//            throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
//        case _ => throw new IllegalArgumentException(s"Prerequisite '$actionName' NOT found")
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