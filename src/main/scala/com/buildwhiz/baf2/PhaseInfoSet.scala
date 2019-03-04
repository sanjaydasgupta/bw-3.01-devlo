package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class PhaseInfoSet extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val fullNames: Map[String, String] = Map(
        ("name", "name"), ("description", "description"),
        ("phase_id", "phase_id")
      )
      val parameterNames = fullNames.keys.toSeq
      val postData = Document.parse(getStreamData(request))
      val unknownParameters = postData.keySet.toArray.filterNot(parameterNames.contains)
      if (unknownParameters.nonEmpty)
        throw new IllegalArgumentException(s"""Unknown parameter(s): ${unknownParameters.mkString(", ")}""")
      if (!postData.containsKey("phase_id"))
        throw new IllegalArgumentException("phase_id not provided")
      val phaseOid = new ObjectId(postData.remove("phase_id").asInstanceOf[String])
      val mongoDbNameValuePairs = parameterNames.filter(postData.containsKey).
          map(paramName => (fullNames(paramName), postData.getString(paramName)))
      if (mongoDbNameValuePairs.isEmpty)
        throw new IllegalArgumentException("No parameters found")
      val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
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