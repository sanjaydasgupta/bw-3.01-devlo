package com.buildwhiz.baf3

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import com.buildwhiz.baf2.ActivityApi
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class TaskInfoSet extends HttpServlet with HttpUtils with DateTimeUtils {

  private def id2oid(id: String) = new ObjectId(id)

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)

    val parameters = getParameterMap(request).filterNot(_._1 == "JSESSIONID")
    try {
      val parameterConverters: Map[String, (String => Any, String)] = Map(
        ("description", (d => d, "description")),
        ("activity_id", (id2oid, "activity_id"))
      )

      val parameterNames = parameterConverters.keys.toSeq
      val unknownParameters = parameters.keySet.toArray.filterNot(parameterNames.contains)
      if (unknownParameters.nonEmpty)
        throw new IllegalArgumentException(s"""Unknown parameter(s): ${unknownParameters.mkString(", ")}""")

      if (!parameters.contains("activity_id"))
        throw new IllegalArgumentException("activity_id not provided")

      val activityOid = new ObjectId(parameters.remove("activity_id").get)
      val activityName = ActivityApi.activityById(activityOid).name[String]

      val mongoDbNameValuePairs = parameterNames.filter(parameters.contains).
          map(paramName => (parameterConverters(paramName)._2, parameterConverters(paramName)._1(parameters(paramName))))
      if (mongoDbNameValuePairs.isEmpty)
        throw new IllegalArgumentException("No parameters found")

      val updateResult = BWMongoDB3.tasks.updateOne(Map("_id" -> activityOid),
          Map("$set" -> mongoDbNameValuePairs.toMap))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

      response.getWriter.print(successJson())
      response.setContentType("application/json")
      val message = s"""set parameters ${mongoDbNameValuePairs.map(_.toString()).mkString(", ")} of '$activityName'"""
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}