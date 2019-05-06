package com.buildwhiz.baf2

import com.buildwhiz.baf2.ActivityApi.teamAssignment
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class ActivityAssignments extends HttpServlet with HttpUtils {

  private def activityAssignments(activity: DynDoc, fill: Boolean): Seq[Document] = {
    val process = ActivityApi.parentProcess(activity._id[ObjectId])
    val assignments: Seq[DynDoc] = teamAssignment.list(activity._id[ObjectId])
    assignments.map(assignment => {
      val assignmentDoc = new Document("_id", assignment._id[ObjectId]).append("role", assignment.role[String]).
        append("activity_name", activity.name[String]).append("activity_id", activity._id[ObjectId].toString).
        append("process_name", process.name[String]).
        append("can_delete", true)
        //append("can_delete", assignment.role[String] != activity.role[String])
      if (assignment.has("organization_id")) {
        val orgOid = assignment.organization_id[ObjectId]
        assignmentDoc.append("organization_id", orgOid)
        val org = OrganizationApi.organizationById(orgOid)
        assignmentDoc.append("organization_name", org.name[String])
      } else {
        assignmentDoc.append("organization_id", "")
        assignmentDoc.append("organization_name", if (fill) "Some Organization" else "")
      }
      if (assignment.has("person_id")) {
        val personOid = assignment.person_id[ObjectId]
        assignmentDoc.append("person_id", personOid)
        val person = PersonApi.personById(personOid)
        assignmentDoc.append("person_name", s"${person.first_name[String]} ${person.last_name[String]}")
      } else {
        assignmentDoc.append("person_id", "")
        assignmentDoc.append("person_name", if (fill) "Some Person" else "")
      }
      if (assignment.has("individual_role")) {
        val indRole = assignment.individual_role[String]
        assignmentDoc.append("individual_role", indRole)
      } else {
        assignmentDoc.append("individual_role", if (fill) "Some-Role" else "")
      }
      if (assignment.has("document_access")) {
        val docAccess = assignment.doc_access[String]
        assignmentDoc.append("document_access", docAccess)
      } else {
        assignmentDoc.append("document_access", if (fill) "Some, Doc, Access" else "")
      }
      assignmentDoc
    })
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      //val user: DynDoc = getUser(request)
      //val userOid = user._id[ObjectId]
      val optProjectOid = parameters.get("project_id").map(new ObjectId(_))
      val optPhaseOid = parameters.get("phase_id").map(new ObjectId(_))
      val activities = (optPhaseOid, optProjectOid) match {
        case (Some(phaseOid), _) => PhaseApi.allActivities(phaseOid)
        case (None, Some(projectOid)) => ProjectApi.allActivities(projectOid)
        case _ => throw new IllegalArgumentException("Required parameters not provided")
      }
      val fill = optPhaseOid match {
        case None => false
        case Some(poid) =>
          val thePhase = PhaseApi.phaseById(poid)
          thePhase.name[String] == "ntov-01-11"
      }
      val assignments = activities.flatMap(activityAssignments(_, fill))

      val assignmentList = assignments.map(bson2json).mkString("[", ", ", "]")
      response.getWriter.print(assignmentList)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}