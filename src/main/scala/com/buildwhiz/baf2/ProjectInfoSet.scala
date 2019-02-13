package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class ProjectInfoSet extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val fullNames: Map[String, String] = Map(
        ("name", "name"), ("description", "description"),
        ("state_name", "address.state.name"), ("country_name", "address.country.name"),
        ("gps_latitude", "address.gps_location.latitude"), ("gps_longitude", "address.gps_location.longitude"),
        ("address_line1", "address.line1"), ("address_line2", "address.line2"), ("address_line3", "address.line3"),
        ("postal_code", "address.postal_code"),
        ("construction_type", "construction_type"), ("type", "type"), ("budget_mm_usd", "budget_mm_usd"),
        ("construction_area_sqft", "construction_area_sqft"), ("land_area_acres", "land_area_acres"),
        ("max_building_height_ft", "max_building_height_ft"), ("project_id", "project_id"))
      val parameterNames = fullNames.keys.toSeq
      val postData = Document.parse(getStreamData(request))
      val unknownParameters = postData.keySet.toArray.filterNot(parameterNames.contains)
      if (unknownParameters.nonEmpty)
        throw new IllegalArgumentException(s"""Unknown parameter(s): ${unknownParameters.mkString(", ")}""")
      if (!postData.containsKey("project_id"))
        throw new IllegalArgumentException("project_id not provided")
      val projectOid = new ObjectId(postData.remove("project_id").asInstanceOf[String])
      val mongoDbNameValuePairs = parameterNames.filter(postData.containsKey).
          map(paramName => (fullNames(paramName), postData.getString(paramName)))
      if (mongoDbNameValuePairs.isEmpty)
        throw new IllegalArgumentException("No parameters found")
      val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid),
          Map("$set" -> mongoDbNameValuePairs.toMap))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${mongoDbNameValuePairs.length}", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}