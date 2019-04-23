package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class ActivityAssignments extends HttpServlet with HttpUtils {

  private def activityAssignments(activity: DynDoc): Seq[Document] = {
    val baseRole = activity.role[String]
    val process = ActivityApi.parentProcess(activity._id[ObjectId])
    if (activity.has("assignments")) {
      val assignments: Seq[DynDoc] = activity.assignments[Many[Document]]
      assignments.map(assignment => {
        val assignmentDoc = new Document("role", assignment.role[String]).append("name", activity.name[String]).
          append("_id", activity._id[ObjectId].toString).append("process_name", process.name[String]).
          append("can_delete", assignment.role[String] != activity.role[String])
        if (assignment.has("organization_id")) {
          val orgOid = assignment.organization_id[ObjectId]
          assignmentDoc.append("organization_id", orgOid)
          val org = OrganizationApi.organizationById(orgOid)
          assignmentDoc.append("organization_name", org.name[String])
        } else {
          assignmentDoc.append("organization_id", "")
          assignmentDoc.append("organization_name", "")
        }
        if (assignment.has("person_id")) {
          val personOid = assignment.person_id[ObjectId]
          assignmentDoc.append("person_id", personOid)
          val person = PersonApi.personById(personOid)
          assignmentDoc.append("person_name", s"${person.first_name[String]} ${person.last_name[String]}")
        } else {
          assignmentDoc.append("person_id", "")
          assignmentDoc.append("person_name", "")
        }
        if (assignment.has("individual_role")) {
          val indRole = assignment.individual_role[String]
          assignmentDoc.append("individual_role", indRole)
        } else {
          assignmentDoc.append("individual_role", "")
        }
        if (assignment.has("doc_access")) {
          val docAccess = assignment.doc_access[String]
          assignmentDoc.append("doc_access", docAccess)
        } else {
          assignmentDoc.append("doc_access", "")
        }
        assignmentDoc
      })
    } else {
      Seq(new Document("_id", activity._id[ObjectId].toString).append("name", activity.name[String]).
        append("role", baseRole).append("organization_name", "").
        append("individual_role", "").append("person_name", "").append("process_name", process.name[String]).
        append("person_id", "").append("organization_id", "").append("doc_access", "").append("can_delete", false))
    }

  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      val optProjectOid = parameters.get("project_id").map(new ObjectId(_))
      val optPhaseOid = parameters.get("phase_id").map(new ObjectId(_))
      val activities = (optPhaseOid, optProjectOid) match {
        case (Some(phaseOid), _) => PhaseApi.allActivities(phaseOid)
        case (None, Some(projectOid)) => ProjectApi.allActivities(projectOid)
        case _ => Nil: Seq[DynDoc]
      }
      val assignments = activities.flatMap(activityAssignments)

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