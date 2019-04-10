package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class PersonInfo extends HttpServlet with HttpUtils {

  private def isEditable(person: DynDoc, user: DynDoc): Boolean = {
    val userOid = user._id[ObjectId]
    // ToDo: change following line to return proper value ...
    true
  }

  private def wrap(value: Any, editable: Boolean): Document = {
    new Document("editable", editable).append("value", value.toString)
  }

  private def person2json(person: DynDoc, editable: Boolean): String = {
    val firstName = new Document("editable", editable).append("value", person.first_name[String])
    val lastName = new Document("editable", editable).append("value", person.last_name[String])

    val yearsExperience = wrap(person.years_experience[Double], editable)
    val rating = wrap(person.rating[Int], editable)
    val skills = wrap(person.skills[Many[String]], editable)
    val active = wrap(person.enabled[Boolean], editable)
    val emails: Seq[DynDoc] = person.emails[Many[Document]]
    val workEmail = wrap(emails.find(_.`type`[String] == "work").get.email[String], editable)
    val phones: Seq[DynDoc] = person.phones[Many[Document]]
    val bareWorkPhone = phones.find(_.`type`[String] == "work") match {
      case None => ""
      case Some(workPhoneRecord) => workPhoneRecord.phone[String]
    }
    val workPhone = wrap(bareWorkPhone, editable)
    val workAddress = wrap(if (person.has("work_address"))
      person.work_address[String]
    else
      "", editable)

    val personDoc = new Document("first_name", firstName).append("last_name", lastName).append("rating", rating).
        append("skills", skills).append("years_experience", yearsExperience).append("active", active).
        append("work_email", workEmail).append("work_phone", workPhone).append("work_address", workAddress).
        append("project_log", Seq.empty[Document].asJava).append("review_log", Seq.empty[Document].asJava)
    bson2json(personDoc)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val personRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
      val user: DynDoc = getUser(request)
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