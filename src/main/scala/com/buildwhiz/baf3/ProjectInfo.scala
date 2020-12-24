package com.buildwhiz.baf3

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId
import com.buildwhiz.baf2.{OrganizationApi, PersonApi, PhaseApi, ProjectApi}

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.collection.JavaConverters._

class ProjectInfo extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val projectRecord: DynDoc = ProjectApi.projectById(projectOid)
      response.getWriter.print(ProjectInfo.project2json(projectRecord, request))
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

object ProjectInfo extends HttpUtils {

  private def isEditable(project: DynDoc, user: DynDoc): Boolean = {
    val userOid = user._id[ObjectId]
    ProjectApi.canManage(userOid, project) || PersonApi.isBuildWhizAdmin(Right(user))
  }

  private def phaseInformation(project: DynDoc): Many[Document] = {
    val phaseOids: Seq[ObjectId] = project.phase_ids[Many[ObjectId]]
    val phases: Seq[DynDoc] = BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> phaseOids)))
    val returnValue: Seq[Document] = phases.map(phase => {
      val displayStatus = PhaseApi.displayStatus(phase)
      Map("name" -> phase.name[String], "status" -> displayStatus, "display_status" -> displayStatus,
        "start_date" -> "NA", "end_date" -> "NA", "_id" -> phase._id[ObjectId].toString)
    })
    returnValue.asJava
  }

  private def fieldSpecification(project: DynDoc, structuredName: String, editable: Boolean, defaultValue: Any = ""):
      Document = {
    val names = structuredName.split("/").map(_.trim)
    val storedValue = names.init.foldLeft(project.asDoc)((dd, s) =>
      dd.getOrDefault(s, new Document()).asInstanceOf[Document]).
      getOrDefault(names.last, defaultValue.toString).toString
    val value = if (storedValue == "" && defaultValue != "") defaultValue else storedValue
    new Document("editable", editable).append("value", value)
  }

  def project2json(project: DynDoc, request: HttpServletRequest, doLog: Boolean = false): String = {
    if(doLog)
      BWLogger.log(getClass.getName, "project2json", s"ENTRY", request)
    val user: DynDoc = getUser(request)
    val editable = ProjectInfo.isEditable(project, user)
    val bareDocumentTags: Seq[String] = if (project.has("document_tags")) {
      project.document_tags[Many[Document]].map(tagSpec => {
        val name = tagSpec.name[String]
        if (tagSpec.has("logic")) s"$name => ${tagSpec.logic[String]}" else name
      })
    } else
      Seq.empty[String].asJava
    val documentTags = new Document("editable", false).append("value", bareDocumentTags.asJava)
    val description = new Document("editable", editable).append("value", project.description[String])
    val rawStatus = project.status[String]
    val status = new Document("editable", false).append("value", rawStatus)
    val displayStatus = new Document("editable", false).
        append("value", ProjectApi.displayStatus2(project, PersonApi.isBuildWhizAdmin(Right(user))))
    val rawName = project.name[String]
    val name = new Document("editable", editable).append("value", rawName)
    val rawSummary = project.get[String]("summary") match {
      case None => s"Summary for '$rawName'"
      case Some(theSummary) => theSummary
    }
    val summary = new Document("editable", editable).append("value", rawSummary)
    val rawGoals = project.get[String]("goals") match {
      case None => s"Goals for '$rawName'"
      case Some(theGoals) => theGoals
    }
    val goals = new Document("editable", editable).append("value", rawGoals)
    val rawCustomerName = project.get[ObjectId]("customer_organization_id") match {
      case None => "Not available"
      case Some(custOrgId) => OrganizationApi.organizationById(custOrgId).name[String]
    }
    val customerName = new Document("editable", false).append("value", rawCustomerName)
    val postalCode = fieldSpecification(project, "address/postal_code", editable)
    val line1 = fieldSpecification(project, "address/line1", editable)
    val line2 = fieldSpecification(project, "address/line2", editable)
    val line3 = fieldSpecification(project, "address/line3", editable)
    val latitude = fieldSpecification(project, "address/gps_location/latitude", editable, defaultValue = 51.4934)
    val longitude = fieldSpecification(project, "address/gps_location/longitude", editable, defaultValue = 0.0098)
    val stateName = fieldSpecification(project, "address/state/name", editable)
    val countryName = fieldSpecification(project, "address/country/name", editable, "United States")
    val constructionType = fieldSpecification(project, "construction_type", editable, "Concrete")
    val projectType = fieldSpecification(project, "type", editable, "Building")
    val buildingUse = fieldSpecification(project, "building_use", editable, "Mixed-Use Facility")
    val budgetMmUsd = fieldSpecification(project, "budget_mm_usd", editable, 0.0)
    val constAreaSqFt = fieldSpecification(project, "construction_area_sqft", editable, 0.0)
    val totalFloorArea = fieldSpecification(project, "total_floor_area", editable, 0.0)
    val landAreaAcres = fieldSpecification(project, "land_area_acres", editable, 0.0)
    val maxBldgHeightFt = fieldSpecification(project, "max_building_height_ft", editable, 0.0)
    val rawProjectManagers = project.assigned_roles[Many[Document]].
        filter(_.role_name[String].matches("(?i)Project-Manager")).map(role => {
          val thePerson = PersonApi.personById(role.person_id[ObjectId])
          val personName = PersonApi.fullName(thePerson)
          new Document("_id", thePerson._id[ObjectId].toString).append("name", personName)
        }).asJava
    val projectManagers = new Document("editable", editable).append("value", rawProjectManagers)
    val phaseInfo = new Document("editable", false).append("value", phaseInformation(project))
    val projectDoc = new Document("name", name).append("summary", summary).append("description", description).
        append("status", status).append("display_status", displayStatus).append("goals", goals).
        append("document_tags", documentTags).append("project_managers", projectManagers).
        append("type", projectType).append("project_type", projectType).append("building_use", buildingUse).
        append("construction_type", constructionType).append("image_url", ProjectApi.imageUrl(Right(project))).
        append("budget_mm_usd", budgetMmUsd).append("budget", budgetMmUsd).append("customer", customerName).
        append("construction_area_sqft", constAreaSqFt).append("building_footprint", constAreaSqFt).
        append("land_area_acres", landAreaAcres).append("site_area", landAreaAcres).
        append("max_building_height_ft", maxBldgHeightFt).append("building_height", maxBldgHeightFt).
        append("phase_info", phaseInfo).append("total_floor_area", totalFloorArea).
        append("address_line1", line1).append("address_line2", line2).append("address_line3", line3).
        append("gps_latitude", latitude).append("gps_longitude", longitude).append("display_edit_buttons", editable).
        append("country_name", countryName).append("state_name", stateName).append("postal_code", postalCode)
    if(doLog)
      BWLogger.log(getClass.getName, "project2json", "EXIT-OK", request)
    projectDoc.toJson
  }

}