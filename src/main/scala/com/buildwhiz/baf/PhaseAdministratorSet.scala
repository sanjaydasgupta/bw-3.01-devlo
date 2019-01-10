package com.buildwhiz.baf

import com.buildwhiz.api.Project
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import org.bson.types.ObjectId

class PhaseAdministratorSet extends HttpServlet with HttpUtils with MailUtils {

  private def saveAndSendMail(request: HttpServletRequest, assignedPersonOid: ObjectId, deAssignedPersonOid: ObjectId,
      projectOid: ObjectId, phaseName: String): Unit = {
    BWLogger.log(getClass.getName, "saveAndSendMail()", "ENTRY", request)
    try {
      val subject1 = "Manager assignment"
      val message1 = s"You have been assigned the role of phase-manager for phase '$phaseName'"
      BWMongoDB3.mails.insertOne(Map("project_id" -> projectOid, "timestamp" -> System.currentTimeMillis,
        "recipient_person_id" -> assignedPersonOid, "subject" -> subject1, "message" -> message1))
      sendMail(Seq(assignedPersonOid), subject1, message1, Some(request))
      val subject2 = "Manager de-assignment"
      val message2 = s"You have been de-assigned from the role of phase-manager for phase '$phaseName'"
      BWMongoDB3.mails.insertOne(Map("project_id" -> projectOid, "timestamp" -> System.currentTimeMillis,
        "recipient_person_id" -> deAssignedPersonOid, "subject" -> subject2, "message" -> message2))
      sendMail(Seq(deAssignedPersonOid), subject2, message2, Some(request))
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
      val phaseOid = new ObjectId(parameters("phase_id"))
      val thePhase: DynDoc = BWMongoDB3.processes.find(Map("_id" -> phaseOid)).head
      val parentProject: DynDoc = BWMongoDB3.projects.find(Map("process_ids" -> phaseOid)).head
      val user: DynDoc = getUser(request)
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      val isAdmin = freshUserRecord.roles[Many[String]].contains("BW-Admin")
      if (!isAdmin && freshUserRecord._id[ObjectId] != parentProject.admin_person_id[ObjectId])
        throw new IllegalArgumentException("Not permitted")
      val deAssignedPersonOid = thePhase.admin_person_id[ObjectId]
      val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> phaseOid),
        Map("$set" -> Map(s"admin_person_id" -> assignedPersonOid)))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      Project.renewUserAssociations(request, Some(parentProject._id[ObjectId]))
      saveAndSendMail(request, assignedPersonOid, deAssignedPersonOid, new ObjectId(parameters("project_id")),
          thePhase.name[String])
      response.setStatus(HttpServletResponse.SC_OK)
      val phaseLog = s"'${thePhase.name[String]}' ($phaseOid)"
      BWLogger.audit(getClass.getName, "doPost", s"""Set manager of phase $phaseLog""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
