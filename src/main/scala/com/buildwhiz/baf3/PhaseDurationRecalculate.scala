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
      if (!postData.has("duration_values"))
        throw new IllegalArgumentException("'duration_values' not provided")
      val durationValues: Seq[DynDoc] = postData.duration_values[Many[Document]]
      val badDurations = durationValues.filter(dv => !dv.has("activity_id") || !dv.has("duration_likely"))
      if (badDurations.nonEmpty)
        throw new IllegalArgumentException(s"found duration_values without 'activity_id' and/or 'duration_likely'")
      val oidAndDurationMap: Map[ObjectId, Int] = durationValues.map(durationValue => {
        val activityOid = new ObjectId(durationValue.activity_id[String])
        val durationLikely = durationValue.duration_likely[String].toInt
        (activityOid, durationLikely)
      }).toMap
      val bpmnName = postData.bpmn_name[String]
      val phaseOid = new ObjectId(postData.phase_id[String])
      val phaseDuration = ProcessBpmnTraverse.processDurationRecalculate(bpmnName, phaseOid, oidAndDurationMap, request)
      val returnedValue = new Document("ok", 1).append("phase_duration_optimistic", "NA").
          append("phase_duration_pessimistic", "NA").append("phase_duration_likely", phaseDuration.toString).toJson
      response.getWriter.print(returnedValue)
      response.setContentType("application/json")
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK ($returnedValue)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}