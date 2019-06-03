package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class TaskStatusInfoSet extends HttpServlet with HttpUtils with DateTimeUtils {

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
      val parameterConverters: Map[String, (String => Any, String)] = Map(
        ("estimated_start_date", (date2ms, "bpmn_scheduled_start_date")),
        ("estimated_end_date", (date2ms, "bpmn_scheduled_end_date")),
        ("actual_start_date", (date2ms, "bpmn_actual_start_date")),
        ("actual_end_date", (date2ms, "bpmn_actual_end_date")),
        ("reporting_interval", (validateReportingInterval, "reporting_interval")),
        ("activity_id", (id2oid, "activity_id"))
      )

      val parameterNames = parameterConverters.keys.toSeq
      val unknownParameters = parameters.keySet.toArray.filterNot(parameterNames.contains)
      if (unknownParameters.nonEmpty)
        throw new IllegalArgumentException(s"""Unknown parameter(s): ${unknownParameters.mkString(", ")}""")

      if (!parameters.contains("activity_id"))
        throw new IllegalArgumentException("activity_id not provided")

      val activityOid = new ObjectId(parameters.remove("activity_id").get)

      val mongoDbNameValuePairs = parameterNames.filter(parameters.contains).
          map(paramName => (parameterConverters(paramName)._2, parameterConverters(paramName)._1(parameters(paramName))))
      if (mongoDbNameValuePairs.isEmpty)
        throw new IllegalArgumentException("No parameters found")

      val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
          Map("$set" -> mongoDbNameValuePairs.toMap))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

      val user: DynDoc = getUser(request)

      val formattedChanges = parameterNames.filter(parameters.contains).map(p => s"$p=${parameters(p)}").mkString(", ")
      val changeEntry = s"Updated fields: $formattedChanges"
      ActivityApi.addChangeLogEntry(activityOid, changeEntry, Some(user._id[ObjectId]), None)

      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.audit(getClass.getName, request.getMethod, changeEntry, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}