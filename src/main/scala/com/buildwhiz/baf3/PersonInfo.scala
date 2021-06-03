package com.buildwhiz.baf3

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.collection.JavaConverters._

class PersonInfo extends HttpServlet with HttpUtils {

  private def isEditable(person: DynDoc, user: DynDoc): Boolean = {
    val userIsAdmin = PersonApi.isBuildWhizAdmin(Right(user))
    val inSameOrganization = if (user.has("organization_id") && person.has("organization_id"))
      user.organization_id[ObjectId] == person.organization_id[ObjectId]
    else
      false
    userIsAdmin || inSameOrganization
  }

  private def wrap(value: Any, editable: Boolean): Document = {
    new Document("editable", editable).append("value", value/*.toString*/)
  }

  private def person2json(person: DynDoc, editable: Boolean): String = {
    val firstName = new Document("editable", editable).append("value", person.first_name[String])
    val lastName = new Document("editable", editable).append("value", person.last_name[String])

    val yearsExperience = wrap(person.years_experience[Double], editable)
    val rating = wrap(person.rating[Int], editable)
    val position = person.get[String]("position") match {
      case Some(pos) => wrap(pos, editable)
      case None => wrap("", editable)
    }
    val skills = wrap(person.skills[Many[String]], editable)
    val active = wrap(if (person.has("enabled")) person.enabled[Boolean] else false, editable)
    val emails: Seq[DynDoc] = person.emails[Many[Document]]
    val workEmail = wrap(emails.find(_.`type`[String] == "work").get.email[String], editable)
    val phones: Seq[DynDoc] = person.phones[Many[Document]]
    val phoneCanText = wrap(if(person.has("phone_can_text")) person.phone_can_text[Boolean] else false, editable)
    val bareWorkPhone = phones.find(_.`type`[String] == "work") match {
      case None => ""
      case Some(workPhoneRecord) => workPhoneRecord.phone[String]
    }
    val workPhone = wrap(bareWorkPhone, editable)
    val bareMobilePhone = phones.find(_.`type`[String] == "mobile") match {
      case None => ""
      case Some(mobilePhoneRecord) => mobilePhoneRecord.phone[String]
    }
    val mobilePhone = wrap(bareMobilePhone, editable)
    val workAddress = wrap(if (person.has("work_address"))
      person.work_address[String]
    else
      "", editable)
    val rawIndividualRoles: java.util.Collection[String] =
      if (person.has("individual_roles"))
        person.individual_roles[Many[String]]
      else
        Seq.empty[String].asJava
    val individualRoles = wrap(rawIndividualRoles, editable)

    val personDoc = new Document("first_name", firstName).append("last_name", lastName).append("rating", rating).
        append("skills", skills).append("years_experience", yearsExperience).append("active", active).
        append("work_email", workEmail).append("work_phone", workPhone).append("work_address", workAddress).
        append("phone_can_text", phoneCanText).append("individual_roles", individualRoles).
        append("project_log", Seq.empty[Document].asJava).append("review_log", Seq.empty[Document].asJava).
        append("mobile_phone", mobilePhone).append("position", position)
    bson2json(personDoc)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val personRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
      val user: DynDoc = getPersona(request)
      val personIsEditable = isEditable(personRecord, user)
      response.getWriter.print(person2json(personRecord, personIsEditable))
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