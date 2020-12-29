package com.buildwhiz.baf3

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import com.buildwhiz.baf2.{OrganizationApi, PersonApi, ProjectApi, RoleListSecondary}

import scala.collection.JavaConverters._

class OrganizationList extends HttpServlet with HttpUtils with DateTimeUtils {

  private def orgDynDocToDocument(org: DynDoc): Document = {
    new Document("_id", org._id[ObjectId].toString).append("name", org.name[String]).append("rating", org.rating[Int]).
      append("reference", org.reference[String]).append("years_experience", org.years_experience[Double]).
      append("skills", org.skills[Seq[String]]).append("active", org.active[Boolean])
  }

  private def organizationList(optOrganizationType: Option[String] = None, optSkill: Option[String] = None):
      Seq[Document] = {
    val organizations = OrganizationApi.fetch(optSkill = optSkill, optOrgType = optOrganizationType)
    organizations.map(orgDynDocToDocument)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val user: DynDoc = getUser(request)
      val optOrganizationType = parameters.get("organization_type")
      val optProjectOid = parameters.get("project_id").map(new ObjectId(_))
      val optPhaseOid = parameters.get("phase_id").map(new ObjectId(_))
      val optActivityOids = parameters.get("activity_id").map(_.split(",").map(id => new ObjectId(id.trim)))
      val skillParameter: Option[String] = parameters.get("skill")
      val isAdmin = PersonApi.isBuildWhizAdmin(Right(user))
      val myProjects = ProjectApi.projectsByUser(user._id[ObjectId])
      def matchSkill(organization: DynDoc): Boolean = {
        skillParameter match {
          case None => true
          case Some(aSkill) =>
            organization.get[Many[String]]("skills") match {
              case None => false
              case Some(skills) => skills.contains(aSkill)
            }
        }
      }
      def matchOrganizationType(organization: DynDoc): Boolean = {
        optOrganizationType match {
          case None => true
          case Some(aType) =>
            organization.get[String]("organization_type") match {
              case None => false
              case Some(orgType) => orgType == aType
            }
        }
      }
      val myProjectsOrganizations: Seq[DynDoc] = myProjects.flatMap(_.get[ObjectId]("customer_organization_id").
          map(cOid => OrganizationApi.organizationById(cOid))).distinct.filter(matchSkill).filter(matchOrganizationType)
      val displayAllOrganizations: Boolean = isAdmin || optProjectOid.
          map(pOid => ProjectApi.canManage(user._id[ObjectId], ProjectApi.projectById(pOid))).isDefined

      val allOrganizations: Seq[DynDoc] = if (displayAllOrganizations) {
        (OrganizationApi.fetch(optOrgType = optOrganizationType, optSkill = skillParameter) ++ myProjectsOrganizations).distinct
      } else {
        myProjectsOrganizations
      }

      val organizations: Seq[DynDoc] =
          (optOrganizationType, skillParameter, optActivityOids, optPhaseOid, optProjectOid) match {
        case (_, Some(skill), _, _, _) =>
          if (RoleListSecondary.secondaryRoles.contains(skill) || skill == "none")
          //if (RoleListSecondary.secondaryRoles.contains(skill))
            allOrganizations
          else
            organizationList(optOrganizationType = optOrganizationType, optSkill = skillParameter)
        case (_, _, Some(_), _, _) => allOrganizations
        case (_, _, _, Some(_), _) => allOrganizations
        case (_, _, _, _, Some(_)) => allOrganizations
        case _ => allOrganizations
      }
      val organizationDetails: Many[Document] = organizations.distinct.sortBy(d => d.name[String]).map(_.asDoc).asJava
      val result = new Document("organization_list", organizationDetails).append("can_add_organization", isAdmin)
      response.getWriter.print(result.toJson)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${organizations.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}