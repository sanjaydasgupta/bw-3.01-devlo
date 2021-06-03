package com.buildwhiz.baf3

import com.buildwhiz.baf2.{PersonApi, OrganizationApi}
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class PartnerInfo extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val organizationOid = new ObjectId(parameters("organization_id"))
      val orgRecord: DynDoc = OrganizationApi.organizationById(organizationOid)
      response.getWriter.print(PartnerInfo.partner2Document(orgRecord, getPersona(request)).toJson)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}

object PartnerInfo extends DateTimeUtils {

  private def wrap(value: Any, editable: Boolean) = new Document("editable", editable).append("value", value)

  def partner2Document(org: DynDoc, user: DynDoc): Document = {
    val editable = PersonApi.isBuildWhizAdmin(Right(user))
    val projectSponsor: Boolean = org.get[Boolean]("project_sponsor") match {
      case Some(ps) => ps
      case None => false
    }
    val designPartner: Boolean = org.get[Boolean]("design_partner") match {
      case Some(dp) => dp
      case None => false
    }
    val tradePartner: Boolean = org.get[Boolean]("trade_partner") match {
      case Some(tp) => tp
      case None => false
    }
    val areasOfOperation: Many[String] = org.get[Many[String]]("areas_of_operation") match {
      case Some(tp) => tp
      case None => Seq.empty[String].asJava
    }
    val profile: String = org.get[String]("profile") match {
      case Some(tp) => tp
      case None => ""
    }
    val pastProjects: String = org.get[String]("past_projects") match {
      case Some(tp) => tp
      case None => ""
    }
    val reviews: String = org.get[String]("reviews") match {
      case Some(tp) => tp
      case None => ""
    }
    val preferences: String = org.get[String]("preferences") match {
      case Some(tp) => tp
      case None => ""
    }
    new Document("_id", org._id[ObjectId].toString).append("name", wrap(org.name[String], editable)).
        append("rating", wrap(org.rating[Int], editable)).append("active", wrap(org.active[Boolean], editable)).
        append("project_sponsor", wrap(projectSponsor, editable)).append("design_partner", wrap(designPartner, editable)).
        append("trade_partner", wrap(tradePartner, editable)).append("skills", wrap(org.skills[Seq[String]], editable)).
        append("serving_area", wrap(areasOfOperation, editable)).append("profile", wrap(profile, editable)).
        append("past_projects", wrap(pastProjects, editable)).append("profile", wrap(profile, editable)).
        append("reviews", wrap(reviews, editable)).append("preferences", wrap(preferences, editable))
  }

}