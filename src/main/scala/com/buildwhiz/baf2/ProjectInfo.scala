package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.jdk.CollectionConverters._

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
    ProjectApi.canManage(userOid, project)
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
    val value = names.init.foldLeft(project.asDoc)((dd, s) =>
      dd.getOrDefault(s, new Document()).asInstanceOf[Document]).
      getOrDefault(names.last, defaultValue.toString).toString
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
    val displayStatus = new Document("editable", false).append("value", ProjectApi.displayStatus(project))
    val name = new Document("editable", editable).append("value", project.name[String])
    val postalCode = fieldSpecification(project, "address/postal_code", editable)
    val line1 = fieldSpecification(project, "address/line1", editable)
    val line2 = fieldSpecification(project, "address/line2", editable)
    val line3 = fieldSpecification(project, "address/line3", editable)
    val latitude = fieldSpecification(project, "address/gps_location/latitude", editable)
    val longitude = fieldSpecification(project, "address/gps_location/longitude", editable)
    val stateName = fieldSpecification(project, "address/state/name", editable)
    val countryName = fieldSpecification(project, "address/country/name", editable,
      "United States")
    val constructionType = fieldSpecification(project, "construction_type", editable,
      "concrete")
    val `type` = fieldSpecification(project, "type", editable, "Mixed-Use Facility")
    val budgetMmUsd = fieldSpecification(project, "budget_mm_usd", editable, 0.0)
    val constAreaSqFt = fieldSpecification(project, "construction_area_sqft", editable,
      0.0)
    val landAreaAcres = fieldSpecification(project, "land_area_acres", editable, 0.0)
    val maxBldgHeightFt = fieldSpecification(project, "max_building_height_ft", editable,
      0.0)
    val phaseInfo = new Document("editable", false).append("value", phaseInformation(project))
    val projectDoc = new Document("name", name).append("description", description).
      append("status", status).append("display_status", displayStatus).
      append("document_tags", documentTags).
      append("type", `type`).append("construction_type", constructionType).append("budget_mm_usd", budgetMmUsd).
      append("construction_area_sqft", constAreaSqFt).append("land_area_acres", landAreaAcres).
      append("max_building_height_ft", maxBldgHeightFt).append("phase_info", phaseInfo).
      append("address_line1", line1).append("address_line2", line2).append("address_line3", line3).
      append("gps_latitude", latitude).append("gps_longitude", longitude).
      append("country_name", countryName).append("state_name", stateName).append("postal_code", postalCode)
    if(doLog)
      BWLogger.log(getClass.getName, "project2json", "EXIT-OK", request)
    projectDoc.toJson
  }

}