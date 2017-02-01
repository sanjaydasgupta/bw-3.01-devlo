package com.buildwhiz.jelly

import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, MailUtils}
import org.bson.types.ObjectId
import org.camunda.bpm.engine.delegate.{DelegateExecution, JavaDelegate}

import scala.collection.JavaConverters._

class EvaluateReviewReports extends JavaDelegate with MailUtils {

  private def saveAndSendMail(action: DynDoc, projectOid: ObjectId, failedReviewNames: Seq[String]): Unit = {
    BWLogger.log(getClass.getName, "saveAndSendMail()", "ENTRY")
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
      sendMail(recipientOid, subject, message)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "saveAndSendMail()", s"ERROR ${t.getClass.getName}(${t.getMessage})")
    }
    BWLogger.log(getClass.getName, "saveAndSendMail()", "EXIT-OK")
  }

  def execute(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "execute()", "ENTRY", de)
    try {
      val activityOid = new ObjectId(de.getVariable("activity_id").asInstanceOf[String])
      val theActivity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).asScala.head
      val actions: Seq[DynDoc] = theActivity.actions[DocumentList]
      val reviewActions: Seq[DynDoc] = actions.filter(_.`type`[String] == "review")
      // Copy review documents to main action's inbox
      val reviewDocOids: ObjectIdList = reviewActions.flatMap(_.outbox[ObjectIdList].asScala).asJava
      val mainActionIdx: Int = actions.map(_.`type`[String]).indexOf("main")
      for (oid <- reviewDocOids.asScala) {
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
      saveAndSendMail(mainAction, new ObjectId(de.getVariable("project_id").asInstanceOf[String]), failedReviewNames)

      BWLogger.log(getClass.getName, s"execute(): ${reviewActions.length} reviews, ${reviewDocOids.asScala.length} documents",
        "EXIT-OK", de)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "execute()", s"ERROR ${t.getClass.getName}(${t.getMessage})", de)
        throw t
    }
  }

}
