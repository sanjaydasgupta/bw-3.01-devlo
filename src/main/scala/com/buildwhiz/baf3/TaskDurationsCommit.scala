package com.buildwhiz.baf3

import com.buildwhiz.baf2.ActivityApi
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class TaskDurationsCommit extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val parameterString = getStreamData(request)
      BWLogger.log(getClass.getName, request.getMethod, s"Parameter-String: $parameterString", request)
      val postData: DynDoc = Document.parse(parameterString)
      if (!postData.has("duration_values"))
        throw new IllegalArgumentException("'duration_values' not provided")
      val durationValues: Seq[DynDoc] = postData.duration_values[Many[Document]]
      for (durationValue <- durationValues) {
        if (!durationValue.has("activity_id")) {
          throw new IllegalArgumentException("missing 'activity_id'")
        }
        ActivityApi.durationsSet3(new ObjectId(durationValue.activity_id[String]),
            durationValue.get[String]("duration_optimistic").map(_.toInt),
            durationValue.get[String]("duration_pessimistic").map(_.toInt),
            durationValue.get[String]("duration_likely").map(_.toInt))
      }
      response.getWriter.print(successJson())
      response.setContentType("application/json")
      val message = s"changed durations of ${durationValues.length} tasks"
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}