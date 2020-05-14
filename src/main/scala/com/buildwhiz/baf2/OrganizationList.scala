package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class OrganizationList extends HttpServlet with HttpUtils with DateTimeUtils {

  private def organizationList(optSkill: Option[String] = None): Seq[Document] = {
    val organizations = OrganizationApi.fetch(None, None, optSkill)
    organizations.map(org => {
      new Document("_id", org._id[ObjectId].toString).append("name", org.name[String]).
        append("reference", org.reference[String]).append("years_experience", org.years_experience[Double]).
        append("rating", org.rating[Int]).append("skills", org.skills[Seq[String]]).
        append("active", org.active[Boolean])
    })
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val optProjectOid = parameters.get("project_id").map(new ObjectId(_))
      val optPhaseOid = parameters.get("phase_id").map(new ObjectId(_))
      val optActivityOids = parameters.get("activity_id").map(_.split(",").map(id => new ObjectId(id.trim)))
      val skillParameter: Option[String] = None //parameters.get("skill")

      val allOrganizations = organizationList()

      val organizations: Seq[Document] = (skillParameter, optActivityOids, optPhaseOid, optProjectOid) match {
        case (Some(skill), _, _, _) =>
          if (RoleListSecondary.secondaryRoles.contains(skill) || skill == "none")
          //if (RoleListSecondary.secondaryRoles.contains(skill))
            allOrganizations
          else
            organizationList(skillParameter)
        case (_, Some(_), _, _) => allOrganizations
        case (_, _, Some(_), _) => allOrganizations
        case (_, _, _, Some(_)) => allOrganizations
        case _ => allOrganizations
      }
      //val organizations: Seq[DynDoc] = Seq.empty[DynDoc]
      val user: DynDoc = getUser(request)
//      val detail = parameters.get("detail") match {
//        case None => false
//        case Some(dv) => dv.toBoolean
//      }
      //val parentPhase: DynDoc = ???
      val canManage = PersonApi.isBuildWhizAdmin(Right(user))
//      if (detail) {
//        val parentProject: DynDoc = ???
//        val organizationDetails: java.util.List[Document] = organizations.
//            map(process => ProcessApi.processProcess(process, parentProject, user._id[ObjectId]).asDoc).asJava
//        val result = new Document("organization_list", organizationDetails).append("can_add_organization", canManage)
//        response.getWriter.print(result.toJson)
//      } else {
        val organizationDetails: java.util.List[Document] = organizations.sortBy(d => d.getString("name")).asJava
        val result = new Document("organization_list", organizationDetails).append("can_add_organization", canManage)
        response.getWriter.print(result.toJson)
//      }
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