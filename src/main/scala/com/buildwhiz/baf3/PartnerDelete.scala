package com.buildwhiz.baf3

import com.buildwhiz.baf2.{OrganizationApi, PersonApi, PhaseApi}
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class PartnerDelete extends HttpServlet with HttpUtils {

  private def mannedProjects(personOids: Seq[ObjectId]): Seq[DynDoc] = {
    val directlyMannedProjects: Seq[DynDoc] = BWMongoDB3.projects.
        find(Map("assigned_roles" -> Map($elemMatch -> Map("person_id" -> Map($in -> personOids)))))
    val mannedPhases: Seq[DynDoc] = BWMongoDB3.phases.
        find(Map("assigned_roles" -> Map($elemMatch -> Map("person_id" -> Map($in -> personOids)))))
    val indirectlyMannedProjects = mannedPhases.map(phase => PhaseApi.parentProject(phase._id[ObjectId]))
    (directlyMannedProjects ++ indirectlyMannedProjects).distinct
  }

  private def teamedWithProjects(organizationOid: ObjectId): Seq[DynDoc] = {
    val engagedTeams: Seq[DynDoc] = BWMongoDB3.teams.find(Map("organization_id" -> organizationOid))
    val teamOids: Seq[ObjectId] = engagedTeams.map(_._id[ObjectId])
    val teamedWithPhases: Seq[DynDoc] = BWMongoDB3.phases.
        find(Map("team_assignments" -> Map($elemMatch -> Map("team_id" -> Map($in -> teamOids)))))
    teamedWithPhases.map(phase => PhaseApi.parentProject(phase._id[ObjectId])).distinct
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val user: DynDoc = getPersona(request)
      if (!PersonApi.isBuildWhizAdmin(Right(user))) {
        throw new IllegalArgumentException("Not permitted")
      }

      val organizationOid = new ObjectId(parameters("organization_id"))
      val organizationRecord = OrganizationApi.organizationById(organizationOid)
      val organizationMembers: Seq[DynDoc] = BWMongoDB3.persons.find(Map("organization_id" -> organizationOid))
      val memberOids = organizationMembers.map(_._id[ObjectId])

      val staffedProjects = mannedProjects(memberOids)
      val sponsoredProjects: Seq[DynDoc] = BWMongoDB3.projects.find(Map("customer_organization_id" -> organizationOid))
      val involvedInProjects = (staffedProjects ++ sponsoredProjects).distinct
      val teamedProjects = teamedWithProjects(organizationOid)
      val projectNames = (involvedInProjects ++ teamedProjects).distinct.map(_.name[String])
      if (projectNames.nonEmpty)
        throw new IllegalArgumentException(s"""Partner involved in projects: ${projectNames.mkString(", ")}""")

      val deleteResult = BWMongoDB3.organizations.deleteOne(Map("_id" -> organizationOid))
      if (deleteResult.getDeletedCount == 0) {
        throw new IllegalArgumentException(s"MongoDB update failed: $deleteResult")
      } else if (organizationMembers.nonEmpty) {
        val deleteResult = BWMongoDB3.persons.deleteOne(Map("organization_id" -> organizationOid))
        if (deleteResult.getDeletedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $deleteResult")
      }

      response.getWriter.print(successJson())
      response.setContentType("application/json")
      val message = s"deleted organization '${organizationRecord.name[String]} and ${organizationMembers.length} members'"
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        reportFatalException(t, getClass.getName, request, response)
    }
  }

}