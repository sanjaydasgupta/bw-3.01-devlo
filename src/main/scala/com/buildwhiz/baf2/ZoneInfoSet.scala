package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class ZoneInfoSet extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val parameters = getParameterMap(request)

      def nop(input: String): Any = input
      val parameterConverters: Map[String, (String => Any, String)] = Map(
        ("name", (nop, "name")), ("description", (nop, "description")), ("area", (nop, "area")),
        ("location", (nop, "location")), ("zone_id", (nop, "zone_id"))
      )

      val parameterNames = parameterConverters.keys.toSeq
      val unknownParameters = parameters.keySet.toArray.filterNot(parameterNames.contains)
      if (unknownParameters.nonEmpty)
        throw new IllegalArgumentException(s"""Unknown parameter(s): ${unknownParameters.mkString(", ")}""")

      if (!parameters.contains("zone_id"))
        throw new IllegalArgumentException("zone_id not provided")

      val zoneOid = new ObjectId(parameters.remove("zone_id").get)

      val mongoDbNameValuePairs = parameterNames.filter(parameters.contains).
        map(paramName => (parameterConverters(paramName)._2, parameterConverters(paramName)._1(parameters(paramName))))
      if (mongoDbNameValuePairs.isEmpty)
        throw new IllegalArgumentException("No parameters found")

      val updateResult = BWMongoDB3.zones.updateOne(Map("_id" -> zoneOid),
          Map("$set" -> mongoDbNameValuePairs.toMap))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

      response.setStatus(HttpServletResponse.SC_OK)
      val parametersChanged = mongoDbNameValuePairs.map(_._1).mkString(", ")
      val message = s"""Updated parameters $parametersChanged of zone $zoneOid"""
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}