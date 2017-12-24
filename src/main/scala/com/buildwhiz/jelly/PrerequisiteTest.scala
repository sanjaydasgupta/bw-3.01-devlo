package com.buildwhiz.jelly

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import BWMongoDB3._
import com.buildwhiz.utils.BWLogger
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.engine.delegate.{DelegateExecution, JavaDelegate}

import scala.collection.JavaConverters._

class PrerequisiteTest extends JavaDelegate {

  def execute(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "execute()", "ENTRY", de)
    try {
      val query = Map("_id" -> new ObjectId(de.getVariable("activity_id").asInstanceOf[String]))
      val activity: DynDoc = BWMongoDB3.activities.find(query).head
      val actions: Seq[DynDoc] = activity.actions[Many[Document]]
      val actionsWithIndex = actions.zipWithIndex
      val actionName = de.getVariable("action_name")
      actionsWithIndex.find(a => a._1.name[String] == actionName && a._1.`type`[String] == "prerequisite").
        map(awi => (awi._1.status[String], awi._2)) match {
        case Some(("ready", idx: Int)) =>
          BWLogger.log(getClass.getName, "execute()", "Matching satisfied prerequisite found", de)
          de.setVariable("prerequisite_ok", true)
          val updateResult = BWMongoDB3.activities.updateOne(query,
            Map("$set" -> Map(s"actions.$idx.status" -> "ended",
              s"actions.$idx.timestamps.end" -> System.currentTimeMillis)))
          if (updateResult.getModifiedCount == 0)
            throw new IllegalArgumentException(s"MongoDB error: $updateResult")
        case Some(("defined", _: Int)) =>
          BWLogger.log(getClass.getName, "execute()", s"prerequisite '$actionName' NOT yet satisfied", de)
        case Some((status: String, _: Int)) =>
          throw new IllegalArgumentException(s"Prerequisite '$actionName' in unexpected state: $status")
        case None =>
          throw new IllegalArgumentException(s"Prerequisite '$actionName' NOT found")
        case _ =>
          throw new IllegalArgumentException(s"Prerequisite '$actionName' in unexpected state")
      }
      BWLogger.log(getClass.getName, "execute()", "EXIT-OK", de)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "execute()", s"ERROR ${t.getClass.getName}(${t.getMessage})", de)
        throw t
    }
  }

}
