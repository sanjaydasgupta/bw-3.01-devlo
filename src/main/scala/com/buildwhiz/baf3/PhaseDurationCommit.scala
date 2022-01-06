package com.buildwhiz.baf3

import com.buildwhiz.baf2.{ActivityApi, PhaseApi}
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class PhaseDurationCommit extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val parameterString = getStreamData(request)
      BWLogger.log(getClass.getName, request.getMethod, s"Parameter-String: $parameterString", request)
      val postData: DynDoc = Document.parse(parameterString)
      if (!postData.has("duration_values"))
        throw new IllegalArgumentException("'duration_values' not provided")
      val durationValues: Seq[DynDoc] = postData.duration_values[Many[Document]]
      val badDurations = durationValues.filter(dv => !(dv.has("activity_id") && dv.has("duration_likely")) &&
          !(dv.has("timer_id") && dv.has("duration")))
      if (badDurations.nonEmpty)
        throw new IllegalArgumentException("found duration_values without expected fields")
      for (durationValue <- durationValues) {
        if (durationValue.has("activity_id")) {
          ActivityApi.durationsSet3(new ObjectId(durationValue.activity_id[String]),
            durationValue.get[String]("duration_optimistic").map(_.toInt),
            durationValue.get[String]("duration_pessimistic").map(_.toInt),
            durationValue.get[String]("duration_likely").map(_.toInt))
        } else if (durationValue.has("timer_id")) {
          val bpmnName = postData.bpmn_name[String]
          val phaseOid = new ObjectId(postData.phase_id[String])
          val theProcess = PhaseApi.allProcesses(phaseOid).headOption match {
            case Some(proc) => proc
            case None => throw new IllegalArgumentException(s"Bad phase: $phaseOid")
          }
          TimerDurationSet.set(request, theProcess, Some(durationValue.timer_id[String]), None, bpmnName,
              durationValue.duration[String])
        } else {
          throw new IllegalArgumentException("\"found duration_values without expected fields\"")
        }
      }
      response.getWriter.print(successJson())
      response.setContentType("application/json")
      val message = s"changed duration of ${durationValues.length} task and/or timer element(s)"
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}