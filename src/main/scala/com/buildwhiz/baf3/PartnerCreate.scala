package com.buildwhiz.baf3

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import com.buildwhiz.baf2.{OrganizationApi, PersonApi}

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.collection.JavaConverters._

class PartnerCreate extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val user: DynDoc = getPersona(request)
      if (!PersonApi.isBuildWhizAdmin(Right(user))) {
        throw new IllegalArgumentException("Not permitted")
      }

      val organizationName = parameters("name").trim
      OrganizationApi.validateNewName(organizationName)
      val reference: String = parameters.get("reference") match {
        case Some(ref) => ref
        case None => ""
      }

      val yearsExperience: Double = parameters.get("years_experience") match {
        case Some(experience) => experience.toDouble
        case None => 0.0
      }

      val ratingValue: Int = parameters.get("rating") match {
        case Some(rating) =>
          if (rating.matches("[1-5]"))
            rating.toInt
          else
            throw new IllegalArgumentException(s"Bad rating value: '$rating'")
        case None => 0
      }

      val skillsValue: Many[String] = (parameters.get("skills") match {
        case Some(skills) => skills.split(",").map(_.trim).toSeq
        case None => Seq.empty[String]
      }).asJava

      val activeValue: Boolean = parameters.get("active") match {
        case Some(active) => active.toBoolean
        case None => false
      }

      val projectSponsor: Boolean = parameters.get("project_sponsor") match {
        case Some(ps) => ps.toBoolean
        case None => false
      }
      val designPartner: Boolean = parameters.get("design_partner") match {
        case Some(dp) => dp.toBoolean
        case None => false
      }
      val tradePartner: Boolean = parameters.get("trade_partner") match {
        case Some(tp) => tp.toBoolean
        case None => false
      }

      val profile = parameters.get("profile") match {
        case Some(prof) => prof
        case None => ""
      }

      val areasOfOperation: Many[String] = (parameters.get("serving_area") match {
        case Some(tp) => tp.split(",").map(_.trim).toSeq
        case None => Seq.empty[String]
      }).asJava

      val newPartnerRecord: Document = Map("name" -> organizationName, "reference" -> reference,
          "years_experience" -> yearsExperience, "rating" -> ratingValue, "skills" -> skillsValue,
          "project_sponsor" -> projectSponsor, "design_partner" -> designPartner, "trade_partner" -> tradePartner,
          "active" -> activeValue, "areas_of_operation" -> areasOfOperation, "profile" -> profile,
          "timestamps" -> Map("created" -> System.currentTimeMillis))
      BWMongoDB3.organizations.insertOne(newPartnerRecord)

      val organizationString = bson2json(newPartnerRecord)
      response.getWriter.print(successJson())
      response.setContentType("application/json")
      val message = s"Created organization '$organizationString'"
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}