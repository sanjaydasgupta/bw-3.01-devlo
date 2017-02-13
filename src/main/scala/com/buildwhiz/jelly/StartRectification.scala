package com.buildwhiz.jelly

import java.util

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.BWLogger
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.engine.delegate.{DelegateExecution, JavaDelegate}

import scala.collection.JavaConverters._

class StartRectification extends JavaDelegate {

  def execute(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "execute()", "ENTRY", de)
    try {
      val query = Map("_id" -> new ObjectId(de.getVariable("activity_id").asInstanceOf[String]))
      val activity: DynDoc = BWMongoDB3.activities.find(query).head
      val actions: Seq[DynDoc] = activity.actions[Many[Document]]

      val failedReviewNames: Seq[String] = actions.filter(_.`type`[String] == "review").
        filterNot(_.review_ok[Boolean]).map(_.name[String])
      val failedReviewNamesList = new util.ArrayList[String]()
      failedReviewNamesList.addAll(failedReviewNames.asJava)
      de.setVariable("review_action_names", failedReviewNamesList)

      val mainAction = actions.filter(_.`type`[String] == "main").head
      val mainName = mainAction.name[String]
      de.setVariable("action_name", mainName)

      BWLogger.log(getClass.getName, "execute()", "EXIT-OK", de)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "execute()", s"ERROR ${t.getClass.getName}(${t.getMessage})", de)
        throw t
    }
  }

}
