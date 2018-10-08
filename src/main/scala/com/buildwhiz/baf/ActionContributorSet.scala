package com.buildwhiz.baf

import com.buildwhiz.api.Project
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import org.bson.Document
import org.bson.types.ObjectId

class ActionContributorSet extends HttpServlet with HttpUtils with MailUtils {

  private def saveAndSendMail(assignedPersonOid: ObjectId, deAssignedPersonOid: ObjectId, projectOid: ObjectId,
        actionName: String, request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getName, "saveAndSendMail()", "ENTRY", request)
    try {
      val subject1 = "Action role assignment"
      val message1 = s"You have been assigned the role of contributor for action '$actionName'"
      BWMongoDB3.mails.insertOne(Map("project_id" -> projectOid, "timestamp" -> System.currentTimeMillis,
        "recipient_person_id" -> assignedPersonOid, "subject" -> subject1, "message" -> message1))
      sendMail(assignedPersonOid, subject1, message1, Some(request))
      val subject2 = "Action role de-assignment"
      val message2 = s"You have been de-assigned from the role of contributor for action '$actionName'"
      BWMongoDB3.mails.insertOne(Map("project_id" -> projectOid, "timestamp" -> System.currentTimeMillis,
        "recipient_person_id" -> deAssignedPersonOid, "subject" -> subject2, "message" -> message2))
      sendMail(deAssignedPersonOid, subject2, message2, Some(request))
    } catch {
      case t: Throwable =>
        //t.printStackTrace()
        BWLogger.log(getClass.getName, "saveAndSendMail()", s"ERROR ${t.getClass.getName}(${t.getMessage})", request)
        throw t
    }
    BWLogger.log(getClass.getName, "saveAndSendMail()", "EXIT-OK", request)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val assignedPersonOid = new ObjectId(parameters("person_id"))
      val activityOid = new ObjectId(parameters("activity_id"))
      val theActivity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).head
      val parentPhase: DynDoc = BWMongoDB3.phases.find(Map("activity_ids" -> activityOid)).head
      val user: DynDoc = getUser(request)
      if (user._id[ObjectId] != parentPhase.admin_person_id[ObjectId])
        throw new IllegalArgumentException("Not permitted")
      val actionNames: Seq[String] = theActivity.actions[Many[Document]].map(_.name[String])
      val actionName = parameters("action_name")
      val actionIdx = actionNames.indexOf(actionName)
      val actions: Seq[DynDoc] = theActivity.actions[Many[Document]]
      val deAssignedPersonOid = actions(actionIdx).assignee_person_id[ObjectId]
      val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
        Map("$set" -> Map(s"actions.$actionIdx.assignee_person_id" -> assignedPersonOid)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      val parentProject: DynDoc = BWMongoDB3.projects.find(Map("phase_ids" -> parentPhase._id[ObjectId])).head
      Project.renewUserAssociations(request, Some(parentProject._id[ObjectId]))
      saveAndSendMail(assignedPersonOid, deAssignedPersonOid, new ObjectId(parameters("project_id")), actionName, request)
      response.setStatus(HttpServletResponse.SC_OK)
      val actionLog = s"'$actionName'"
      BWLogger.audit(getClass.getName, "doPost", s"""Set contributor of action $actionLog""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
