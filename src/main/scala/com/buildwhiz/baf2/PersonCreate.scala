package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, CryptoUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class PersonCreate extends HttpServlet with HttpUtils with CryptoUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      if (!PersonApi.isBuildWhizAdmin(userOid)) {
        // Disabled temporarily for testing ...
        //  throw new IllegalArgumentException("Not permitted")
      }

      val organizationOid = new ObjectId(parameters("organization_id"))
      if (!OrganizationApi.exists(organizationOid))
        throw new IllegalArgumentException(s"bad organization_id '$organizationOid'")

      val workEmail = parameters("work_email")

      if (PersonApi.fetch(Some(workEmail)).nonEmpty)
        throw new IllegalArgumentException(s"Email '$workEmail' is already used")

      val firstName = parameters("first_name")
      val lastName = parameters("last_name")

      val yearsExperience: Double = parameters.get("years_experience") match {
        case Some(experience) =>
          if (experience.matches("\\d+(?:\\.\\d+)?"))
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
        case Some(skills) => skills.split(",").map(_.trim).filter(_.trim.nonEmpty)
        case None => Seq.empty[String]
      }

      val individualRoles: Seq[String] = parameters.get("individual_roles") match {
        case Some(indRoles) =>
          val theRoles = indRoles.split(",").map(_.trim).filter(_.trim.nonEmpty)
          theRoles.foreach(role =>
            if (!PersonApi.possibleIndividualRoles.contains(role))
              throw new IllegalArgumentException(s"Bad individual-role: '$role'")
          )
          theRoles
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

      val phoneCanText: Boolean = parameters.get("phone_can_text") match {
        case Some(canText) =>
          if (canText.matches("true|false"))
            canText.toBoolean
          else
            throw new IllegalArgumentException(s"Bad phone_can_text value: '$canText'")
        case None => false
      }

      val workAddress: String = parameters.get("work_address") match {
        case Some(wrkAddr) => wrkAddr
        case None => ""
      }

      val phones: java.util.Collection[Document] = parameters.get("work_phone") match {
        case Some(wrkPhone) => Seq(new Document("type", "work").append("phone", wrkPhone)).asJava
        case None => Seq(new Document("type", "work").append("phone", "")).asJava
      }

      val timezone: String = parameters.get("timezone") match {
        case Some(tz) => tz
        case None => "US/Pacific"
      }

      val newPersonRecord: Document = Map("organization_id" -> organizationOid, "first_name" -> firstName,
          "last_name" -> lastName, "years_experience" -> yearsExperience, "rating" -> ratingValue,
          "skills" -> skillsValue.asJava, "enabled" -> activeValue, "password" -> md5(firstName),
          "emails" -> Seq(new Document("type", "work").append("email", workEmail)).asJava,
          "phone_can_text" -> phoneCanText, "work_address" -> workAddress, "individual_roles" -> individualRoles.asJava,
          "phones" -> phones, "tz" -> timezone, "roles" -> Seq.empty[String].asJava,
          "timestamps" -> Map("created" -> System.currentTimeMillis))
      BWMongoDB3.persons.insertOne(newPersonRecord)

      newPersonRecord.remove("password")
      val personString = bson2json(newPersonRecord)
      response.getWriter.print(personString)
      response.setContentType("application/json")
      val message = s"Created person '$personString'"
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