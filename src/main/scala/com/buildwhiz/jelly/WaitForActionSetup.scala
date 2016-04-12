package com.buildwhiz.jelly

import java.text.SimpleDateFormat
//import java.time.format.DateTimeFormatter
import java.util.{Calendar, Date}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.types.ObjectId
import org.camunda.bpm.engine.delegate.{DelegateExecution, ExecutionListener}

import scala.collection.JavaConversions._

class WaitForActionSetup extends ExecutionListener {

  private def sendMail(action: DynDoc, projectOid: ObjectId): Unit = {
    BWLogger.log(getClass.getName, "sendMail()", "ENTRY")
    val calendar = java.util.Calendar.getInstance()
    try {
      val recipientOid = action.assignee_person_id[ObjectId]
      val subject = "Action Requested"
      val Seq(days, hours, minutes) = action.duration[String].split(":").map(_.toInt).toSeq
      calendar.add(Calendar.DATE, days)
      calendar.add(Calendar.HOUR, hours)
      calendar.add(Calendar.MINUTE, minutes)
      val targetTime = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(calendar.getTimeInMillis))
      val message = s"The action '${action.name[String]}' can now be started, and must be completed by " +
        s"$targetTime"
      BWMongoDB3.mails.insertOne(Map("project_id" -> projectOid, "timestamp" -> System.currentTimeMillis,
        "recipient_person_id" -> recipientOid, "subject" -> subject, "message" -> message))
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "sendMail()", s"ERROR ${t.getClass.getName}(${t.getMessage})")
    }
    BWLogger.log(getClass.getName, "sendMail()", "EXIT-OK")
  }

  def notify(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "notify()", "ENTRY", de)
    try {
      val activityQuery = Map("_id" -> new ObjectId(de.getVariable("activity_id").asInstanceOf[String]))
      val activity: DynDoc = BWMongoDB3.activities.find(activityQuery).head
      val actions: Seq[DynDoc] = activity.actions[DocumentList]
      val actionNames: Seq[String] = actions.map(_.name[String])
      val actionName = de.getVariable("action_name").asInstanceOf[String]
      actionNames.indexOf(actionName) match {
        case -1 =>
          throw new IllegalArgumentException(s"Action '$actionName' NOT found")
        case idx =>
          BWLogger.log(getClass.getName, "notify()", s"Action '$actionName' wait-id: ${de.getId}", de)
          val projectOid = new ObjectId(de.getVariable("project_id").asInstanceOf[String])
          sendMail(actions(idx), projectOid)
          val updateResult = BWMongoDB3.activities.updateOne(activityQuery,
            Map("$set" -> Map(s"actions.$idx.status" -> "waiting", s"actions.$idx.camunda_execution_id" -> de.getId,
            s"actions.$idx.timestamps.start" -> System.currentTimeMillis)))
          if (updateResult.getModifiedCount == 0)
            throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      }
      BWLogger.log(getClass.getName, "notify()", "EXIT-OK")
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "notify()", s"ERROR ${t.getClass.getName}(${t.getMessage})", de)
    }
  }

}
