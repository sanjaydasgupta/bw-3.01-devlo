package com.buildwhiz.jelly

import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import BWMongoDB3._
import org.bson.types.ObjectId
import org.camunda.bpm.engine.delegate.{DelegateExecution, JavaDelegate}

import scala.collection.JavaConversions._

class EvaluateReviewReports extends JavaDelegate {

  private def sendMail(action: DynDoc, projectOid: ObjectId, failedReviewNames: Seq[String]): Unit = {
    BWLogger.log(getClass.getName, "sendMail()", "ENTRY")
    try {
      val recipientOid = action.assignee_person_id[ObjectId]
      val subject = if (failedReviewNames.isEmpty) "Successful action completion" else "Action rework Requested"
      val message = if (failedReviewNames.isEmpty)
        s"The action '${action.name[String]}' has been accepted as successfully completed"
      else
        s"The action '${action.name[String]}' requires rework as described in the following review(s): " +
            s"""${failedReviewNames.mkString(", ")}"""
      BWMongoDB3.mails.insertOne(Map("project_id" -> projectOid, "timestamp" -> System.currentTimeMillis,
        "recipient_person_id" -> recipientOid, "subject" -> subject, "message" -> message))
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "sendMail()", s"ERROR ${t.getClass.getName}(${t.getMessage})")
    }
    BWLogger.log(getClass.getName, "sendMail()", "EXIT-OK")
  }

  def execute(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "execute()", "ENTRY", de)
    try {
      val activityOid = new ObjectId(de.getVariable("activity_id").asInstanceOf[String])
      val theActivity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).head
      val actions: Seq[DynDoc] = theActivity.actions[DocumentList]
      val reviewActions: Seq[DynDoc] = actions.filter(_.`type`[String] == "review")
      // Copy review documents to main action's inbox
      val reviewDocOids: ObjectIdList = reviewActions.flatMap(_.outbox[ObjectIdList])
      val mainActionIdx: Int = actions.map(_.`type`[String]).indexOf("main")
      for (oid <- reviewDocOids) {
        BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
          Map("$addToSet" -> Map(s"actions.$mainActionIdx.inbox" -> oid)))
        //if (updateResult.getModifiedCount == 0)
        //  throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      }
      // Flag success (of all reviews) / failure (of any review)
      val allReviewsOk = reviewActions.forall(_.review_ok[Boolean])
      de.setVariable("all_reviews_ok", allReviewsOk)

      val mainAction = actions.filter(_.`type`[String] == "main").head
      val failedReviewNames = reviewActions.filterNot(_.review_ok[Boolean]).map(_.name[String])
      sendMail(mainAction, new ObjectId(de.getVariable("project_id").asInstanceOf[String]), failedReviewNames)

      BWLogger.log(getClass.getName, s"execute(): ${reviewActions.length} reviews, ${reviewDocOids.length} documents",
        "EXIT-OK", de)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "execute()", s"ERROR ${t.getClass.getName}(${t.getMessage})", de)
        throw t
    }
  }

}
