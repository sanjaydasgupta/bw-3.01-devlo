package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class OrganizationCreate extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val user: DynDoc = getUser(request)
      if (!PersonApi.isBuildWhizAdmin(Right(user))) {
        throw new IllegalArgumentException("Not permitted")
      }

      val organizationName = parameters("name")
      if (OrganizationApi.fetch(name=Some(organizationName)).nonEmpty)
        throw new IllegalArgumentException(s"Organization named '$organizationName' already exists")

      val reference: String = parameters.get("reference") match {
        case Some(ref) => ref
        case None => ""
      }

      val yearsExperience: Double = parameters.get("years_experience") match {
        case Some(experience) =>
          if (experience.matches("\\d+(?:\\.\\d*)?"))
            experience.toDouble
          else
            throw new IllegalArgumentException(s"Bad experience value: '$experience'")
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

      val skillsValue: Seq[String] = parameters.get("skills") match {
        case Some(skills) => skills.split(",").map(_.trim)
        case None => Seq.empty[String]
      }

      val activeValue: Boolean = parameters.get("active") match {
        case Some(active) =>
          if (active.matches("true|false"))
            active.toBoolean
          else
            throw new IllegalArgumentException(s"Bad active value: '$active'")
        case None => false
      }

      val newOrganizationRecord: Document = Map("name" -> organizationName, "reference" -> reference,
          "years_experience" -> yearsExperience, "rating" -> ratingValue, "skills" -> skillsValue.asJava,
          "active" -> activeValue, "timestamps" -> Map("created" -> System.currentTimeMillis))
      BWMongoDB3.organizations.insertOne(newOrganizationRecord)

      val organizationString = bson2json(newOrganizationRecord)
      response.getWriter.print(organizationString)
      response.setContentType("application/json")
      val message = s"Created organization '$organizationString'"
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}