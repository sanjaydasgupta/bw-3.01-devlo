package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
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
      val projectName = parameters("name")
      val description = parameters.get("description") match {
        case Some(desc) => desc
        case None => s"This is the description of the '$projectName' project."
      }
      val user: DynDoc = getUser(request)
      val adminPersonOid = parameters.get("admin_person_id") match {
        case None => user._id[ObjectId]
        case Some(id) => new ObjectId(id)
      }
      val address: DynDoc = Map("line1" -> "First line of the address", "line2" -> "Second line of the address",
        "line3" -> "Third line of the address", "state" -> Map("name" -> "California", "code" -> "CA"),
        "country" -> Map("name" -> "United States", "code" -> "US"), "postal_code" -> "94102",
        "gps_location" -> Map("latitude" -> 37.7857971, "longitude" -> -122.4142195))

      val projectDocument: Document = Map("name" -> projectName, "description" -> description,
        "admin_person_id" -> adminPersonOid, "type" -> "Housing Facility", "construction_type" -> "steel-frame",
        "budget_mm_usd" -> 0.0, "construction_area_sqft" -> 0.0, "land_area_acres" -> 0.0,
        "max_building_height_ft" -> 0.0, "address" -> address, "process_ids" -> Seq.empty[ObjectId],
        "phase_ids" -> Seq.empty[ObjectId], "assigned_roles" -> Seq.empty[Document], "system_labels"-> Seq.empty[String],
        "timestamps" -> Map("created" -> System.currentTimeMillis), "status" -> "defined")
      BWMongoDB3.projects.insertOne(projectDocument)

      BWMongoDB3.persons.updateOne(Map("_id" -> adminPersonOid),
        Map("$addToSet" -> Map("project_ids" -> projectDocument.y._id[ObjectId])))

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
