package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class ProjectCreate extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doPost()", "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> userOid)).head
      val isAdmin = freshUserRecord.roles[Many[String]].contains("BW-Admin")
      if (!isAdmin)
        throw new IllegalArgumentException("Not permitted")

      val projectName = parameters("name")
      val description = parameters.get("description") match {
        case Some(desc) => desc
        case None => s"This is the description of the '$projectName' project."
      }

      val adminPersonOid = parameters.get("admin_person_id") match {
        case None => userOid
        case Some(id) => new ObjectId(id)
      }
      val address: DynDoc = Map("line1" -> "First line of the address", "line2" -> "Second line of the address",
        "line3" -> "Third line of the address", "state" -> Map("name" -> "California", "code" -> "CA"),
        "country" -> Map("name" -> "United States", "code" -> "US"), "postal_code" -> "94102",
        "gps_location" -> Map("latitude" -> 37.7857971, "longitude" -> -122.4142195))

      val systemTags = Seq("Architecture", "Contract", "Current-Plan", "EIR", "Geotech", "HRE", "Invoice",
        "Land-Use", "Meeting-Notes", "Other", "Pre-App-Meeting", "Preservation-Alternatives", "Public-Health",
        "Report", "Soils-Report", "Survey", "Traffic Study", "Wind-Study").
        map(tag => new Document("name", tag))

      val projectDocument: Document = Map("name" -> projectName, "description" -> description,
        "admin_person_id" -> adminPersonOid, "type" -> "Housing Facility", "construction_type" -> "steel-frame",
        "budget_mm_usd" -> 0.0, "construction_area_sqft" -> 0.0, "land_area_acres" -> 0.0,
        "max_building_height_ft" -> 0.0, "address" -> address, "process_ids" -> Seq.empty[ObjectId],
        "phase_ids" -> Seq.empty[ObjectId], "assigned_roles" -> Seq.empty[Document], "document_tags"-> systemTags,
        "timestamps" -> Map("created" -> System.currentTimeMillis), "status" -> "defined")
      BWMongoDB3.projects.insertOne(projectDocument)

      BWMongoDB3.persons.updateOne(Map("_id" -> adminPersonOid),
        Map("$addToSet" -> Map("project_ids" -> projectDocument.y._id[ObjectId])))

      response.getWriter.print(bson2json(projectDocument))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.audit(getClass.getName, "doPost", s"Created Project '$projectName'", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
