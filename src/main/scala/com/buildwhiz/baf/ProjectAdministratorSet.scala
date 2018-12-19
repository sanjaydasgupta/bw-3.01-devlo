package com.buildwhiz.baf

import com.buildwhiz.api.Project
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import org.bson.types.ObjectId

class ProjectAdministratorSet extends HttpServlet with HttpUtils with MailUtils {

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
      val projectOid = new ObjectId(parameters("project_id"))
      val theProject: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val user: DynDoc = getUser(request)
      if (!user.roles[Many[String]].contains("BW-Admin"))
        throw new IllegalArgumentException("Not permitted")
      val deAssignedPersonOid = theProject.admin_person_id[ObjectId]
      val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid),
        Map("$set" -> Map(s"admin_person_id" -> assignedPersonOid)))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      Project.renewUserAssociations(request, Some(theProject._id[ObjectId]))
      saveAndSendMail(request, assignedPersonOid, deAssignedPersonOid, new ObjectId(parameters("project_id")),
          theProject.name[String])
      response.setStatus(HttpServletResponse.SC_OK)
      val projectMgr: DynDoc = BWMongoDB3.persons.find(Map("_id" -> assignedPersonOid)).head
      val fullName = s"${projectMgr.first_name[String]} ${projectMgr.last_name[String]}"
      val message = s"Set '$fullName' as manager of project '${theProject.name[String]}'"
      BWLogger.audit(getClass.getName, "doPost", message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
