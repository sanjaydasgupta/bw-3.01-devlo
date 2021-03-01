package com.buildwhiz.baf3

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
      val mongodbSetter: Seq[(Map[String, ObjectId], Map[String, Int])] = durationValues.map(durationValue => {
        if (!durationValue.has("activity_id")) {
          throw new IllegalArgumentException("missing 'activity_id'")
        }
        val fieldNames = Seq("duration_optimistic", "duration_pessimistic", "duration_likely")
        val durationsMap: Map[String, Int] = fieldNames.map(fn => (fn, durationValue.get[String](fn))).flatMap({
          case (_, None) => None
          case (fieldName, Some(duration)) => Some((fieldName, duration.toInt))
        }).toMap
        (Map("activity_id" -> new ObjectId(durationValue.activity_id[String])), durationsMap)
      })
      //val projectId = postData.remove("project_id").asInstanceOf[String]
      //val nameValuePairs = postData.entrySet.asScala.map(es => (es.getKey, es.getValue.asInstanceOf[String])).toSeq
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