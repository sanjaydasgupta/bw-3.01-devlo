package com.buildwhiz.baf3

import com.buildwhiz.baf2.{OrganizationApi, PersonApi, PhaseApi, ProjectApi}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.collection.JavaConverters._
import scala.math.random

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

object ProjectInfo extends HttpUtils with DateTimeUtils {

  private def isEditable(project: DynDoc, user: DynDoc): Boolean = {
    val userOid = user._id[ObjectId]
    ProjectApi.canManage(userOid, project) || PersonApi.isBuildWhizAdmin(Right(user))
  }

  private def phaseInformation2(project: DynDoc): Many[Document] = {
    def rint(): String = (random() * 15).toInt.toString
    ProjectApi.allPhases(project).map(phase => {
      new Document("name", phase.name[String]).append("_id", phase._id[ObjectId].toString).
          append("display_status", PhaseApi.displayStatus31(phase)).append("alert_count", rint()).
          append("rfi_count", rint()).append("issue_count", rint())
    }).asJava
  }

  private def phaseInformation(project: DynDoc, user: DynDoc): Many[Document] = {
    val phaseOids: Seq[ObjectId] = project.phase_ids[Many[ObjectId]]
    val phases: Seq[DynDoc] = BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> phaseOids)))
    val canManageProject = ProjectApi.canManage(user._id[ObjectId], project)
    val returnValue: Seq[Document] = phases.map(phase => {
      val phaseDisplayStatus = PhaseApi.displayStatus31(phase)
      val budget = (math.random() * 1000).toInt / 100.0
      val expenditure = budget * 0.5
      val estimatedDatesEditable = canManageProject && phaseDisplayStatus == "Planning"
      val timestamps: DynDoc = phase.timestamps[Document]
      val phaseTimezone = PhaseApi.timeZone(phase)
      val startDate = if (timestamps.has("date_start_actual")) {
        dateString(timestamps.date_start_actual[Long], phaseTimezone)
      } else if (timestamps.has("date_start_estimated")) {
        dateString(timestamps.date_start_estimated[Long], phaseTimezone)
      } else {
        "NA"
      }
      val endDate = if (timestamps.has("date_end_actual")) {
        dateString(timestamps.date_end_actual[Long], phaseTimezone)
      } else if (timestamps.has("date_end_estimated")) {
        dateString(timestamps.date_end_estimated[Long], phaseTimezone)
      } else {
        "NA"
      }
      Map("name" -> phase.name[String], "status" -> phaseDisplayStatus, "display_status" -> phaseDisplayStatus,
        "start_date" -> new Document("editable", estimatedDatesEditable).append("value", startDate),
        "end_date" -> new Document("editable", estimatedDatesEditable).append("value", endDate),
        "duration" -> "NA", "_id" -> phase._id[ObjectId].toString,
        "budget" -> f"$budget%5.2f", "expenditure" -> f"$expenditure%5.2f")
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
    val user: DynDoc = getPersona(request)
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
    //val status = new Document("editable", false).append("value", rawStatus)
    val userIsAdmin = PersonApi.isBuildWhizAdmin(Right(user))
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
    val tz = fieldSpecification(project, "tz", editable)
    val rawProjectManagers = project.assigned_roles[Many[Document]].
        filter(_.role_name[String].matches("(?i)Project-Manager")).map(role => {
      val thePerson = PersonApi.personById(role.person_id[ObjectId])
      val personName = PersonApi.fullName(thePerson)
      new Document("_id", thePerson._id[ObjectId].toString).append("name", personName)
    }).asJava
    val projectManagers = new Document("editable", editable).append("value", rawProjectManagers)
    val phaseInfo = phaseInformation(project, user)
    val phaseStatusValues: Seq[String] = phaseInfo.map(_.status[String]).distinct
    val rawDisplayStatus3 = phaseStatusValues.length match {
      case 0 => "Unknown"
      case 1 => phaseStatusValues.head
      case _ => "Active"
    }
    val displayStatus3 = new Document("editable", false).append("value", rawDisplayStatus3)
    val phaseInfo2 = phaseInformation2(project)
    val userCanManageProject = ProjectApi.canManage(user._id[ObjectId], project)
    val canCreatePhase = PersonApi.fullName(user).matches("Prabhas Admin|Sanjay Admin")
    val projectDoc = new Document("name", name).append("summary", summary).append("description", description).
        append("status", displayStatus3).append("display_status", displayStatus3).append("goals", goals).
        append("document_tags", documentTags).append("project_managers", projectManagers).
        append("type", projectType).append("project_type", projectType).append("building_use", buildingUse).
        append("construction_type", constructionType).append("image_url", ProjectApi.imageUrl(Right(project))).
        append("budget_mm_usd", budgetMmUsd).append("budget", budgetMmUsd).append("customer", customerName).
        append("construction_area_sqft", constAreaSqFt).append("building_footprint", constAreaSqFt).
        append("land_area_acres", landAreaAcres).append("site_area", landAreaAcres).
        append("max_building_height_ft", maxBldgHeightFt).append("building_height", maxBldgHeightFt).
        append("phase_info", phaseInfo).append("total_floor_area", totalFloorArea).append("tz", tz).
        append("address_line1", line1).append("address_line2", line2).append("address_line3", line3).
        append("gps_latitude", latitude).append("gps_longitude", longitude).append("display_edit_buttons", editable).
        append("country_name", countryName).append("state_name", stateName).append("postal_code", postalCode).
        append("menu_items", displayedMenuItems(userIsAdmin, userCanManageProject)).
        append("canUploadImage", userCanManageProject).append("phase_info2", phaseInfo2).
        append("can_create_phase", canCreatePhase)
    if(doLog)
      BWLogger.log(getClass.getName, "project2json", "EXIT-OK", request)
    projectDoc.toJson
  }

}