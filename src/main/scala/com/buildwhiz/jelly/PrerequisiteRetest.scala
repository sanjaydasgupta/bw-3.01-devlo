package com.buildwhiz.jelly

import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import BWMongoDB3._
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.engine.delegate.{DelegateExecution, JavaDelegate}

import scala.collection.JavaConverters._

class PrerequisiteRetest extends JavaDelegate {

  def execute(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "execute()", "ENTRY", de)
    try {
      val query = Map("_id" -> new ObjectId(de.getVariable("activity_id").asInstanceOf[String]))
      val activity: Document = BWMongoDB3.activities.find(query).asScala.head
      val actions: Seq[Document] = activity.asScala("actions").asInstanceOf[DocumentList].asScala
      val actionsWithIndex = actions.zipWithIndex
      val prerequisiteName = de.getVariable("action_name")
      actionsWithIndex.find(a => a._1.asScala("name") == prerequisiteName && a._1.asScala("type") == "prerequisite") match {
        case Some(awi) if awi._1.asScala("status") == "ready" =>
          val id = awi._1.asScala("id").asInstanceOf[String]
          BWLogger.log(getClass.getName, "execute()", "Prerequisite satisfied: calling messageEventReceived()", de)
          de.getProcessEngineServices.getRuntimeService.messageEventReceived("prerequisite", id)
          val updateResult = BWMongoDB3.activities.updateOne(query, Map("$set" ->
            Map(s"actions.${awi._2}.status" -> "ended", s"actions.${awi._2}.timestamps.end" -> System.currentTimeMillis)))
          BWLogger.log(getClass.getName, "execute()", s"MongoDB.updateResult: $updateResult", de)
        case Some(awi) =>
          BWLogger.log(getClass.getName, "execute()",
            s"Prerequisite '$prerequisiteName' status: ${awi._1.asScala("status")}, taking no action", de)
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
