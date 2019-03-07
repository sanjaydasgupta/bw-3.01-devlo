package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class TaskInfoSet extends HttpServlet with HttpUtils with DateTimeUtils {

  private def date2ms(date: String) = milliseconds(date)

  private def validateReportingInterval(interval: String): String = {
    if (interval.matches("daily|weekly|biweekly|monthly"))
      interval
    else
      throw new IllegalArgumentException(s"Bad interval: '$interval'")
  }

  private def id2oid(id: String) = new ObjectId(id)

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val parameterConverters: Map[String, String => Any] = Map(
        ("estimated_start_date", date2ms), ("estimated_end_date", date2ms),
        ("reporting_interval", validateReportingInterval), ("activity_id", id2oid))

      val parameterNames = parameterConverters.keys.toSeq
      val unknownParameters = parameters.keySet.toArray.filterNot(parameterNames.contains)
      if (unknownParameters.nonEmpty)
        throw new IllegalArgumentException(s"""Unknown parameter(s): ${unknownParameters.mkString(", ")}""")

      if (!parameters.contains("activity_id"))
        throw new IllegalArgumentException("activity_id not provided")

      val activityOid = new ObjectId(parameters.remove("activity_id").get)

      val mongoDbNameValuePairs = parameterNames.filter(parameters.contains).
          map(paramName => (paramName, parameterConverters(paramName)(parameters(paramName))))
      if (mongoDbNameValuePairs.isEmpty)
        throw new IllegalArgumentException("No parameters found")

      val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
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