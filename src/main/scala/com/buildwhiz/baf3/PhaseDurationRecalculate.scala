package com.buildwhiz.baf3

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class PhaseDurationRecalculate extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val parameterString = getStreamData(request)
      BWLogger.log(getClass.getName, request.getMethod, s"Parameter-String: $parameterString", request)
      val postData: DynDoc = Document.parse(parameterString)
      BWLogger.log(getClass.getName, request.getMethod, s"postData: $postData", request)
      if (!postData.has("duration_values"))
        throw new IllegalArgumentException("'duration_values' not provided")
      val durationValues: Seq[DynDoc] = postData.duration_values[Many[Document]]
      for (durationValue <- durationValues) {
        if (!durationValue.has("activity_id")) {
          throw new IllegalArgumentException("missing 'activity_id'")
        }
        val fieldNames = Seq("duration_optimistic", "duration_pessimistic", "duration_likely")
        val nameValuePairs: Seq[(String, Int)] = fieldNames.flatMap(fieldName =>
          durationValue.get[String](fieldName) match {
            case None => None
            case Some(duration) => Some((fieldName, duration.toInt))
          }
        )
        val activityOid = new ObjectId(durationValue.activity_id[String])
      }
      val returnedValue = new Document("ok", 1).append("phase_duration_optimistic", "NA").
          append("phase_duration_pessimistic", "NA").append("phase_duration_likely", "NA")
      response.getWriter.print(returnedValue.toJson)
      response.setContentType("application/json")
      val message = s"changed durations of ${durationValues.length} tasks"
      BWLogger.log(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}