package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import com.buildwhiz.utils.{HttpUtils, MailUtils}
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class ActionContributorSet extends HttpServlet with HttpUtils with MailUtils {

  private def saveAndSendMail(assignedPersonOid: ObjectId, deAssignedPersonOid: ObjectId, projectOid: ObjectId,
        actionName: String): Unit = {
    BWLogger.log(getClass.getName, "saveAndSendMail()", "ENTRY")
    try {
      val subject1 = "Action role assignment"
      val message1 = s"You have been assigned the role of contributor for action '$actionName'"
      BWMongoDB3.mails.insertOne(Map("project_id" -> projectOid, "timestamp" -> System.currentTimeMillis,
        "recipient_person_id" -> assignedPersonOid, "subject" -> subject1, "message" -> message1))
      sendMail(assignedPersonOid, subject1, message1)
      val subject2 = "Action role de-assignment"
      val message2 = s"You have been de-assigned from the role of contributor for action '$actionName'"
      BWMongoDB3.mails.insertOne(Map("project_id" -> projectOid, "timestamp" -> System.currentTimeMillis,
        "recipient_person_id" -> deAssignedPersonOid, "subject" -> subject2, "message" -> message2))
      sendMail(deAssignedPersonOid, subject2, message2)
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "saveAndSendMail()", s"ERROR ${t.getClass.getName}(${t.getMessage})")
    }
    BWLogger.log(getClass.getName, "saveAndSendMail()", "EXIT-OK")
  }

  private def adjustPersonsProjectIds(personOid: ObjectId): Unit = {
    val projects: Seq[DynDoc] = BWMongoDB3.projects.find().asScala.toSeq
    for (project <- projects) {
      val phaseIds: Seq[ObjectId] = project.phase_ids[ObjectIdList].asScala
      val phases: Seq[DynDoc] = BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> phaseIds))).asScala.toSeq
      val activityIds: Seq[ObjectId] = phases.flatMap(_.activity_ids[ObjectIdList].asScala)
      val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityIds))).asScala.toSeq
      val actions: Seq[DynDoc] = activities.flatMap(_.actions[DocumentList])
      val isAssociated = actions.exists(_.assignee_person_id[ObjectId] == personOid) ||
        phases.exists(_.admin_person_id[ObjectId] == personOid) ||
        projects.exists(_.admin_person_id[ObjectId] == personOid)
      if (isAssociated) {
        val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> personOid),
          Map("$addToSet" -> Map("project_ids" -> project._id[ObjectId])))
        if (updateResult.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
        BWLogger.log(getClass.getName, "adjustProjectIds", s"$isAssociated, $updateResult")
      } else {
        val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> personOid),
          Map("$pull" -> Map("project_ids" -> project._id[ObjectId])))
        if (updateResult.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
        BWLogger.log(getClass.getName, "adjustProjectIds", s"$isAssociated, $updateResult")
      }
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val assignedPersonOid = new ObjectId(parameters("person_id"))
      val activityOid = new ObjectId(parameters("activity_id"))
      val theActivity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).asScala.head
      val actionNames: Seq[String] = theActivity.actions[DocumentList].map(_.name[String])
      val actionName = parameters("action_name")
      val actionIdx = actionNames.indexOf(actionName)
      val actions: Seq[DynDoc] = theActivity.actions[DocumentList]
      val deAssignedPersonOid = actions(actionIdx).assignee_person_id[ObjectId]
      val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
        Map("$set" -> Map(s"actions.$actionIdx.assignee_person_id" -> assignedPersonOid)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      adjustPersonsProjectIds(assignedPersonOid)
      adjustPersonsProjectIds(deAssignedPersonOid)
      saveAndSendMail(assignedPersonOid, deAssignedPersonOid, new ObjectId(parameters("project_id")), actionName)
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
    BWLogger.log(getClass.getName, "doPost", "EXIT-OK", request)
  }
}
