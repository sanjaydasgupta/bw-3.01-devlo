package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class OrganizationList extends HttpServlet with HttpUtils with DateTimeUtils {

  private def organizationList(): Seq[Document] = {
    val organizations = OrganizationApi.fetch()
    organizations.map(org => {
      new Document("_id", org._id[ObjectId].toString).append("name", org.name[String]).
        append("reference", org.reference[String]).append("years_experience", org.years_experience[Double]).
        append("rating", org.rating[Int]).append("skills", org.skills[Seq[String]]).
        append("active", org.active[Boolean])
    })
  }

  private def dummyOrganizationList(n: Int): Seq[Document] = {
    (0 until n).map(i => {
      val (skills, active) = if ((i % 2) == 0)
        ("Alpha, Beta", true)
      else
        ("Gamma, Delta, Zeta", false)
      new Document("name", s"Dummy Organization ${i * 1111}").append("reference", s"Ref-${i * 1111}").
        append("skills", skills).append("years_experience", 9 - math.abs(i - 5)).append("rating", math.abs(i - 5)).
        append("_id", "000000000000000000000000").append("active", active)
    })
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val optProjectOid = parameters.get("project_id").map(new ObjectId(_))
      val optPhaseOid = parameters.get("phase_id").map(new ObjectId(_))
      val optActivityOid = parameters.get("activity_id").map(new ObjectId(_))

      val allOrganizations = {
        val dbOrganizations = organizationList()
        if (dbOrganizations.length < 10)
          dbOrganizations ++ dummyOrganizationList(10 - dbOrganizations.length)
        else
          dbOrganizations
      }

      val organizations: Seq[Document] = (optActivityOid, optPhaseOid, optProjectOid) match {
        case (Some(activityOid), _, _) => allOrganizations
        case (_, Some(phaseOid), _) => allOrganizations
        case (_, _, Some(projectOid)) => allOrganizations
        case _ => allOrganizations
      }
      //val organizations: Seq[DynDoc] = Seq.empty[DynDoc]
      val user: DynDoc = getUser(request)
//      val detail = parameters.get("detail") match {
//        case None => false
//        case Some(dv) => dv.toBoolean
//      }
      //val parentPhase: DynDoc = ???
      val canManage = true //PhaseApi.canManage(user._id[ObjectId], parentPhase)
//      if (detail) {
//        val parentProject: DynDoc = ???
//        val organizationDetails: java.util.List[Document] = organizations.
//            map(process => ProcessApi.processProcess(process, parentProject, user._id[ObjectId]).asDoc).asJava
//        val result = new Document("organization_list", organizationDetails).append("can_add_organization", canManage)
//        response.getWriter.print(result.toJson)
//      } else {
        val organizationDetails: java.util.List[Document] = organizations.asJava
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