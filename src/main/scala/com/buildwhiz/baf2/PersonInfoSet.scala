package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId
import org.bson.Document

import scala.jdk.CollectionConverters._

class PersonInfoSet extends HttpServlet with HttpUtils {

  private def processIndividualRoles(individualRoles: String): Seq[String] = {
    val theRoles = individualRoles.split(",").map(_.trim).filter(_.trim.nonEmpty).toSeq
    theRoles.foreach(role =>
      if (!PersonApi.possibleIndividualRoles.contains(role))
        throw new IllegalArgumentException(s"Bad individual-role: '$role'")
    )
    theRoles
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {

      val parameterMap = getParameterMap(request)

      val personOid = new ObjectId(parameterMap("person_id"))
      val person = PersonApi.personById(personOid)

      val user: DynDoc = getUser(request)
      val userIsAdmin = PersonApi.isBuildWhizAdmin(Right(user))
      val inSameOrganization = if (user.has("organization_id") && person.has("organization_id"))
        user.organization_id[ObjectId] == person.organization_id[ObjectId]
      else
        false
      if (!userIsAdmin && !inSameOrganization)
        throw new IllegalArgumentException("Not permitted")

      if (parameterMap.contains("work_email")) {
        val workEmail = parameterMap("work_email")
        val personsUsingEmail: Seq[DynDoc] = BWMongoDB3.persons.find(Map("emails" -> Map("type" -> "work",
            "email" -> workEmail)))
        personsUsingEmail.map(_._id[ObjectId]) match {
          case Nil =>
          case pid +: Nil if pid == personOid =>
          case _ => throw new IllegalArgumentException(s"email '$workEmail' is already used")
        }
      }

      val tempPerson = PersonApi.personById(personOid)
      if (tempPerson.phones[Many[Document]].indexWhere(_.`type`[String] == "work") == -1) {
        val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> personOid),
          Map("$push" -> Map("phones" -> Map("type" -> "work", "phone" -> ""))))
        if (updateResult.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      }

      def rating2int(rating: String): Int = {
        if (rating.matches("[1-5]"))
          rating.toInt
        else
          throw new IllegalArgumentException(s"bad rating: '$rating'")
      }
      val workEmailIndex = person.emails[Many[Document]].indexWhere(_.`type`[String] == "work")
      if (workEmailIndex == -1)
        throw new IllegalArgumentException("Work email not pre-defined in user record")
      val workPhoneIndex = person.phones[Many[Document]].indexWhere(_.`type`[String] == "work")
      if (workPhoneIndex == -1)
        throw new IllegalArgumentException("Work phone not pre-defined in user record")
      val mobilePhoneIndex = person.phones[Many[Document]].indexWhere(_.`type`[String] == "mobile") match {
        case -1 => person.phones[Many[Document]].length
        case idx => idx
      }
      if (mobilePhoneIndex == -1) {
        val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> personOid),
          Map($push -> Map("phones" -> Map("type" -> "mobile", "phone" -> ""))))
        if (updateResult.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      }

      val parameterConverters: Map[String, (String => Any, String)] = Map(
        ("first_name", (_.trim, "first_name")),
        ("last_name", (_.trim, "last_name")),
        ("work_address", (_.trim, "work_address")),
        ("rating", (rating2int, "rating")),
        ("skills", (_.split(",").map(_.trim).toSeq.filter(_.trim.nonEmpty).asJava, "skills")),
        ("individual_roles", (role => processIndividualRoles(role).asJava, "individual_roles")),
        ("years_experience", (_.toDouble, "years_experience")),
        ("active", (_.toBoolean, "enabled")),
        ("work_email", (_.trim, s"emails.$workEmailIndex.email")),
        ("work_phone", (_.trim, s"phones.$workPhoneIndex.phone")),
        ("mobile_phone", (_.trim, s"phones.$mobilePhoneIndex.phone")),
        ("phone_can_text", (_.toBoolean, "phone_can_text")),
        ("person_id", (new ObjectId(_), "person_id"))
      )
      val unknownParameters = parameterMap.keySet.toArray.filterNot(parameterConverters.contains)
      if (unknownParameters.nonEmpty)
        throw new IllegalArgumentException(s"""Unknown parameter(s): ${unknownParameters.mkString(", ")}""")

      val parameterValues = parameterConverters.map(pc => {
        val paramName = pc._1
        val paramConverter = pc._2._1
        val fieldName = pc._2._2
        val exists = parameterMap.contains(paramName)
        (paramName, paramConverter, fieldName, exists)
      }).filter(_._4).map(t => (t._3, t._2(parameterMap(t._1))))

      val (personIdAndValue, paramNamesAndValues) = parameterValues.partition(_._1 == "person_id")

      if (paramNamesAndValues.isEmpty)
        throw new IllegalArgumentException("No parameters found")

      val organizationOid = person.organization_id[ObjectId]
      (paramNamesAndValues.find(_._1 == "first_name"), paramNamesAndValues.find(_._1 == "last_name")) match {
        case (Some((_, firstName: String)), Some((_, lastName: String))) =>
          PersonApi.validateNewName(firstName, lastName, organizationOid)
        case(Some((_, firstName: String)), None) =>
          PersonApi.validateNewName(firstName, person.last_name[String], organizationOid)
        case(None, Some((_, lastName: String))) =>
          PersonApi.validateNewName(person.first_name[String], lastName, organizationOid)
        case _ =>
      }

      val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> personIdAndValue.head._2),
          Map("$set" -> paramNamesAndValues.toMap))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

      response.setStatus(HttpServletResponse.SC_OK)
      val parametersChanged = paramNamesAndValues.map(_._1).mkString("[", ", ", "]")
      val message = s"""Updated parameters $parametersChanged of ${PersonApi.fullName(person)}"""
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}