package com.buildwhiz.baf3

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}

import com.mongodb.client.model.InsertOneModel
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId
import com.buildwhiz.baf2.{OrganizationApi, PersonApi, ProjectApi}

import scala.jdk.CollectionConverters._

class ProjectCreate extends HttpServlet with HttpUtils {

  private def createProjectTeams(projectOid: ObjectId, personOid: ObjectId, organizationOid: ObjectId): Unit = {
    BWMongoDB3.teams.insertOne(
        Map("project_id" -> projectOid, "team_name" -> "Default PM Team", "organization_id" -> organizationOid,
        "group" -> "Project Management", "skill" -> Seq("Project-Manager (33-25 BW 11)"), "color" -> "#008000",
        "team_members" -> Seq(Map("person_id" -> personOid, "roles" -> Seq("Manager"))), "__v" -> 0))
  }

  private def createProjectTags(projectOid: ObjectId): Unit = {
    val projectTags = Seq("Architecture", "Contract", "Current-Plan", "EIR", "Geotech", "HRE", "Invoice",
        "Land-Use", "Meeting-Notes", "Other", "Pre-App-Meeting", "Preservation-Alternatives", "Public-Health",
        "Report", "Soils-Report", "Survey", "Traffic Study", "Wind-Study")
    val bulkWriteBuffer: Seq[InsertOneModel[Document]] = projectTags.map(tag =>
      new InsertOneModel(new Document("L1", tag).append("project_id", projectOid).append("__v", 0)))
    val bulkWriteResult = BWMongoDB3.project_tags.bulkWrite(bulkWriteBuffer.asJava)
    if (bulkWriteResult.getInsertedCount != projectTags.length) {
      throw new IllegalArgumentException(s"FAILED to insert projectTags: $bulkWriteResult")
    }
    val tagOids: Many[ObjectId] = bulkWriteResult.getInserts.map(bwi => bwi.getId.asObjectId().getValue)
    val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid),
        Map($set -> Map("document_tags" -> tagOids)))
    if (updateResult.getModifiedCount != 1) {
      throw new IllegalArgumentException(s"FAILED to set document_tags ($updateResult) in project record $projectOid")
    }
  }

  private def doPostTransaction(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val loggedInUser = getUser(request)
      if (!PersonApi.fullName(loggedInUser).matches("Prabhas Admin|Sanjay Admin|BW2 Kannektify"))
        throw new IllegalArgumentException("Not permitted")
      val user: DynDoc = getPersona(request)
      val userOid = user._id[ObjectId]
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> userOid)).head
      val isAdmin = PersonApi.isBuildWhizAdmin(Right(freshUserRecord))
      if (!isAdmin)
        throw new IllegalArgumentException("Not permitted")

      val projectName = parameters("name")
      ProjectApi.validateNewName(projectName)
      val description = parameters.get("description") match {
        case Some(desc) => desc
        case None => s"This is the description of the '$projectName' project."
      }

      val adminPersonOid = parameters.get("admin_person_id") match {
        case None => userOid
        case Some(id) => new ObjectId(id)
      }

      val customerOid = new ObjectId(parameters("customer_id"))
      if (!OrganizationApi.exists(customerOid))
        throw new IllegalArgumentException(s"Bad customer_id: $customerOid")

      val address: DynDoc = Map("line1" -> "First line of the address", "line2" -> "Second line of the address",
        "line3" -> "Third line of the address", "state" -> Map("name" -> "California", "code" -> "CA"),
        "country" -> Map("name" -> "United States", "code" -> "US"), "postal_code" -> "94102",
        "gps_location" -> Map("latitude" -> 37.7857971, "longitude" -> -122.4142195))

      val assignedRoles = Seq(Map("role_name" -> "Project-Manager", "person_id" -> adminPersonOid))
      val projectDocument: Document = Map("name" -> projectName, "summary" -> projectName, "description" -> description,
        "admin_person_id" -> adminPersonOid, "type" -> "Building", "construction_type" -> "Concrete",
        "budget_mm_usd" -> 0.0, "construction_area_sqft" -> 0.0, "land_area_acres" -> 0.0,
        "building_use" -> "Mixed-Use Facility",
        "max_building_height_ft" -> 0.0, "address" -> address, "process_ids" -> Seq.empty[ObjectId],
        "phase_ids" -> Seq.empty[ObjectId], "assigned_roles" -> assignedRoles,
        "timestamps" -> Map("created" -> System.currentTimeMillis), "total_floor_area" -> 0.0,
        "status" -> "defined", "customer_organization_id" -> customerOid, "tz" -> "GMT")
      BWMongoDB3.projects.insertOne(projectDocument)
      val projectOid = projectDocument.get("_id").asInstanceOf[ObjectId]
      createProjectTags(projectOid)
      createProjectTeams(projectOid, userOid, user.organization_id[ObjectId])

      response.getWriter.print(successJson())
      response.setContentType("application/json")
      BWLogger.audit(getClass.getName, request.getMethod, s"Created Project '$projectName'", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    doPostTransaction(request, response)
  }
}
