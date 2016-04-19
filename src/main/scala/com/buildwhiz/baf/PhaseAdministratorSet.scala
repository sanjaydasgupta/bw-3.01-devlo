package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.Utils
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.types.ObjectId

import scala.collection.JavaConversions._

class PhaseAdministratorSet extends HttpServlet with Utils {

  private def sendMail(assignedPersonOid: ObjectId, deAssignedPersonOid: ObjectId, projectOid: ObjectId,
        phaseName: String): Unit = {
    BWLogger.log(getClass.getName, "sendMail()", "ENTRY")
    try {
      val subject1 = "Manager assignment"
      val message1 = s"You have been assigned the role of phase-manager for phase '$phaseName'"
      BWMongoDB3.mails.insertOne(Map("project_id" -> projectOid, "timestamp" -> System.currentTimeMillis,
        "recipient_person_id" -> assignedPersonOid, "subject" -> subject1, "message" -> message1))
      val subject2 = "Manager de-assignment"
      val message2 = s"You have been de-assigned from the role of phase-manager for phase '$phaseName'"
      BWMongoDB3.mails.insertOne(Map("project_id" -> projectOid, "timestamp" -> System.currentTimeMillis,
        "recipient_person_id" -> deAssignedPersonOid, "subject" -> subject2, "message" -> message2))
    } catch {
      case t: Throwable =>
        t.printStackTrace()
        BWLogger.log(getClass.getName, "sendMail()", s"ERROR ${t.getClass.getName}(${t.getMessage})")
    }
    BWLogger.log(getClass.getName, "sendMail()", "EXIT-OK")
  }

  private def adjustPersonsProjectIds(personOid: ObjectId): Unit = {
    val projects: Seq[DynDoc] = BWMongoDB3.projects.find().toSeq
    for (project <- projects) {
      val phaseIds: Seq[ObjectId] = project.phase_ids[ObjectIdList]
      val phases: Seq[DynDoc] = BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> phaseIds))).toSeq
      val activityIds: Seq[ObjectId] = phases.flatMap(_.activity_ids[ObjectIdList])
      val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityIds))).toSeq
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
      val phaseOid = new ObjectId(parameters("phase_id"))
      val thePhase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val deAssignedPersonOid = thePhase.admin_person_id[ObjectId]
      val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
        Map("$set" -> Map(s"admin_person_id" -> assignedPersonOid)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      adjustPersonsProjectIds(assignedPersonOid)
      adjustPersonsProjectIds(deAssignedPersonOid)
      sendMail(assignedPersonOid, deAssignedPersonOid, new ObjectId(parameters("project_id")), thePhase.name[String])
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
