package com.buildwhiz.baf3

import com.buildwhiz.baf2.{OrganizationApi, PersonApi}
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class PartnerDelete extends HttpServlet with HttpUtils {
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

      val sponsoredProjects: Seq[DynDoc] = BWMongoDB3.projects.find(Map("customer_organization_id" -> organizationOid))
      val engagedTeams: Seq[DynDoc] = BWMongoDB3.teams.find(Map("organization_id" -> organizationOid))
      val teamOids: Seq[ObjectId] = engagedTeams.map(_._id[ObjectId])
      val teamPhases: Seq[DynDoc] = BWMongoDB3.phases.
          find(Map("team_assignments" -> Map($elemMatch -> Map("team_id" -> Map($in -> teamOids)))))
      val phaseOids = teamPhases.map(_._id[ObjectId]).distinct
      val teamProjects: Seq[DynDoc] = BWMongoDB3.projects.
          find(Map("phase_ids" -> Map($elemMatch -> Map($in -> phaseOids))))
      val projectNames = (sponsoredProjects ++ teamProjects).map(_.name[String]).distinct
      if (projectNames.nonEmpty)
        throw new IllegalArgumentException(s"""Partner involved in projects: ${projectNames.mkString(", ")}""")

      val deleteResult = BWMongoDB3.organizations.deleteOne(Map("_id" -> organizationOid))
      if (deleteResult.getDeletedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $deleteResult")

      response.getWriter.print(successJson())
      response.setContentType("application/json")
      val message = s"deleted organization '${organizationRecord.name[String]}'"
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}