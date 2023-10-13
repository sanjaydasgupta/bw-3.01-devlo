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

  private def createProjectSkills(projectOid: ObjectId): Unit = {
    val newRecord: Document = Map("project_id" -> projectOid, "codes" -> Seq(
      "33-11 00 00", "33-11 21 00", "33-11 51 00", "33-11 61 21", "33-21 00 00", "33-21 11 00", "33-21 11 11",
      "33-21 11 21", "33-21 21 00", "33-21 23 00", "33-21 27 00", "33-21 31 00", "33-21 31 BW", "33-21 31 11",
      "33-21 31 11 BW", "33-21 31 11 11", "33-21 31 14", "33-21 31 17", "33-21 31 17 11", "33-21 31 17 21",
      "33-21 31 17 34", "33-21 31 21", "33-21 31 21 31", "33-21 31 24 21", "33-21 31 99 11",
      "33-21 31 99 21 11", "33-21 31 99 21 21", "33-21 31 99 21 31", "33-21 51 00", "33-21 51 11",
      "33-21 51 16", "33-21 51 19", "33-21 99 10", "33-21 99 25", "33-21 99 28", "33-21 99 31 11",
      "33-21 99 31 13", "33-21 99 46", "33-23 00 00", "33-23 11 00", "33-23 21 00", "33-23 21 11",
      "33-23 21 21", "33-23 21 31", "33-23 41 00", "33-23 51 00", "33-25 00 00", "33-25 11 00", "33-25 11 11",
      "33-25 14 00", "33-25 15 00", "33-25 16 00", "33-25 16 11", "33-25 16 13", "33-25 21 00", "33-25 31 00",
      "33-25 41 00", "33-25 41 21", "33-25 51 00", "33-25 51 11", "33-25 51 13", "33-25 61 00", "33-41 00 00",
      "33-41 01 00", "33-41 01 13", "33-41 01 14", "33-41 01 16", "33-41 01 31", "33-41 03 00",
      "33-41 03 11 11", "33-41 03 21", "33-41 03 31", "33-41 06 00", "33-41 06 11", "33-41 09 00",
      "33-41 09 11", "33-41 10 00", "33-41 10 11", "33-41 10 21", "33-41 21 00", "33-41 21 11", "33-41 24 00",
      "33-41 30 00", "33-41 31 00", "33-41 31 11", "33-41 33 00", "33-41 40 00", "33-41 43 00", "33-41 46 00",
      "33-41 51 00", "33-41 53 00", "33-41 54 00", "33-41 56 00", "33-41 60 00", "33-41 63 00", "33-41 64 00",
      "33-41 64 11", "33-41 64 31", "33-41 73 00", "33-41 76 00", "33-41 76 11", "33-41 79 00", "33-41 81 21",
      "33-41 83 00", "33-41 91 11", "33-55 00 00", "33-55 14 14", "33-55 14 17", "33-55 21 00", "33-55 24 00",
      "33-55 24 14", "33-55 24 21", "33-55 24 23 41", "33-81 00 00", "33-81 11 11", "33-81 11 17",
      "33-81 11 21", "33-81 11 BW", "33-81 11 23", "33-81 21 11", "33-81 21 21 11", "33-81 21 21 13",
      "33-81 31 00", "33-81 31 11", "33-81 31 14", "33-81 31 17", "33-81 31 19", "33-81 31 26").asJava)
    BWMongoDB3.project_omni33classes.insertOne(newRecord)
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

      val customerOid = new ObjectId(parameters("customer_id"))
      if (!OrganizationApi.exists(customerOid))
        throw new IllegalArgumentException(s"Bad customer_id: $customerOid")

      val address: DynDoc = Map("line1" -> "First line of the address", "line2" -> "Second line of the address",
        "line3" -> "Third line of the address", "state" -> Map("name" -> "California", "code" -> "CA"),
        "country" -> Map("name" -> "United States", "code" -> "US"), "postal_code" -> "94102",
        "gps_location" -> Map("latitude" -> 37.7857971, "longitude" -> -122.4142195))

      val assignedRoles = Seq.empty[Document]
      val projectDocument: Document = Map("name" -> projectName, "summary" -> projectName, "description" -> description,
        "type" -> "Building", "construction_type" -> "Concrete",
        "budget_mm_usd" -> 0.0, "construction_area_sqft" -> 0.0, "land_area_acres" -> 0.0,
        "building_use" -> "Mixed-Use Facility",
        "max_building_height_ft" -> 0.0, "address" -> address, "process_ids" -> Seq.empty[ObjectId],
        "phase_ids" -> Seq.empty[ObjectId], "assigned_roles" -> assignedRoles,
        "timestamps" -> Map("created" -> System.currentTimeMillis), "total_floor_area" -> 0.0,
        "status" -> "defined", "customer_organization_id" -> customerOid, "tz" -> "PST",
        "enum_definitions" -> Map("Apt" -> Map("items" -> Seq.empty[String], "removable" -> false)))
      BWMongoDB3.projects.insertOne(projectDocument)
      val projectOid = projectDocument.get("_id").asInstanceOf[ObjectId]
      createProjectTags(projectOid)
      createProjectSkills(projectOid)

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
