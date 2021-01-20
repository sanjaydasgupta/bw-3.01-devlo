package com.buildwhiz.baf3

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class ActivityInfoSet extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val parameterMap = getParameterMap(request).filterNot(_._1 == "JSESSIONID")
      def dateValidation(date: String): Any = {
        LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
        date
      }
      def string2int(input: String): Any = input.toInt
      val parameterConverters: Map[String, (String => Any, String)] = Map(
        ("duration_optimistic", (string2int, "durations.optimistic")),
        ("duration_pessimistic", (string2int, "durations.pessimistic")),
        ("duration_likely", (string2int, "durations.likely")),
        ("date_start", (dateValidation, "date_start")), ("date_end", (dateValidation, "date_end")),
        ("latest_start", (dateValidation, "latest_start"))
      )
      val knownParameterNames = parameterConverters.keys.toSeq
      val unknownParameters = parameterMap.keys.filterNot(knownParameterNames.contains).filterNot(_ == "activity_id")
      if (unknownParameters.nonEmpty)
        throw new IllegalArgumentException(s"""Unknown parameter(s): ${unknownParameters.mkString(", ")}""")
      if (!parameterMap.contains("activity_id"))
        throw new IllegalArgumentException("activity_id not provided")
      val activityOid = new ObjectId(parameterMap("activity_id"))
      val mongoDbNameValuePairs = knownParameterNames.filter(parameterMap.contains).
          map(paramName => (parameterConverters(paramName)._2,
          parameterConverters(paramName)._1(parameterMap(paramName))))
      if (mongoDbNameValuePairs.isEmpty)
        throw new IllegalArgumentException("No parameters found")
      val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
          Map("$set" -> mongoDbNameValuePairs.toMap))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      response.setStatus(HttpServletResponse.SC_OK)
      val parametersChanged = mongoDbNameValuePairs.map(_._1).mkString("[", ", ", "]")
      val logMessage = s"""Updated parameters $parametersChanged of activity $activityOid"""
//      val managers = PhaseApi.managers(Left(activityOid))
//      for (manager <- managers) {
//        SlackApi.sendNotification(logMessage, Right(manager), Some(parentProjectOid), Some(request))
//      }
      response.getWriter.print(successJson())
      response.setContentType("application/json")
      BWLogger.audit(getClass.getName, request.getMethod, logMessage, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}