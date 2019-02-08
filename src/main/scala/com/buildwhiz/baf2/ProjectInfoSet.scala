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
      val parameterConverters: Map[String, String => Any] = Map(
        ("name", s => s), ("description", s => s),
        ("address.state.name", s => s), ("address.state.code", s => s),
        ("address.country.name", s => s), ("address.country.code", s => s),
        ("address.gps_location.latitude", _.toDouble), ("address.gps_location.longitude", _.toDouble),
        ("address.line1", s => s), ("address.line2", s => s), ("address.line3", s => s),
        ("address.postal_code", s => s),
        ("construction_type", s => s), ("type", s => s), ("budget_mm_usd", _.toDouble),
        ("construction_area_sqft", _.toDouble), ("land_area_acres", _.toDouble),
        ("max_building_height_ft", _.toDouble), ("project_id", s => s))
      val parameterNames = parameterConverters.keys.toSeq
      val postData = Document.parse(getStreamData(request))
      val unknownParameters = postData.keySet.toArray.filterNot(parameterNames.contains)
      if (unknownParameters.nonEmpty)
        throw new IllegalArgumentException(s"""Unknown parameter(s): ${unknownParameters.mkString(", ")}""")
      if (!postData.containsKey("project_id"))
        throw new IllegalArgumentException("project_id not provided")
      val projectOid = new ObjectId(postData.remove("project_id").asInstanceOf[String])
      val paramNameValuePairs = parameterNames.filter(postData.containsKey).
          map(paramName => (paramName, parameterConverters(paramName)(postData.getString(paramName))))
      if (paramNameValuePairs.isEmpty)
        throw new IllegalArgumentException("No parameters found")
      val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid),
          Map("$set" -> paramNameValuePairs.toMap))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${paramNameValuePairs.length}", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}