package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class OrganizationInfo extends HttpServlet with HttpUtils {

  private def isEditable(organization: DynDoc, user: DynDoc): Boolean = {
    val userIsAdmin = PersonApi.isBuildWhizAdmin(user._id[ObjectId])
    val inSameOrganization = if (user.has("organization_id"))
      user.organization_id[ObjectId] == organization._id[ObjectId]
    else
      false
    userIsAdmin || inSameOrganization
  }

  private def personInformation(organization: DynDoc): Many[Document] = {
    val persons: Seq[DynDoc] = PersonApi.fetch(None, Some(organization._id[ObjectId]))
    val returnValue: Seq[Document] = persons.map(PersonApi.person2document)
    returnValue.asJava
  }

  private def wrap(value: Any, editable: Boolean): Document = {
    new Document("editable", editable).append("value", value.toString)
  }

  private def organization2json(organization: DynDoc, editable: Boolean): String = {
    val reference = new Document("editable", editable).append("value", organization.reference[String])

    val yearsExperience = wrap(organization.years_experience[Double], editable)
    val rating = wrap(organization.rating[Int], editable)
    val name = wrap(organization.name[String], editable)
    val skills = wrap(organization.skills[Many[String]], editable)
    val active = wrap(organization.active[Boolean], editable)
    val areasOfOperation = wrap(if (organization.has("areas_of_operation"))
      organization.areas_of_operation[String]
    else
      "", editable)

    val projectDoc = new Document("name", name).append("reference", reference).append("rating", rating).
        append("skills", skills).append("years_experience", yearsExperience).append("active", active).
        append("areas_of_operation", areasOfOperation).append("person_information", personInformation(organization)).
        append("project_log", Seq.empty[Document].asJava).append("review_log", Seq.empty[Document].asJava)
    bson2json(projectDoc)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val organizationOid = new ObjectId(parameters("organization_id"))
      val organizationRecord: DynDoc = BWMongoDB3.organizations.find(Map("_id" -> organizationOid)).head
      val user: DynDoc = getUser(request)
      val organizationIsEditable = isEditable(organizationRecord, user)
      response.getWriter.print(organization2json(organizationRecord, organizationIsEditable))
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