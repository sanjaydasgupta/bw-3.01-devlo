package com.buildwhiz.baf3

import com.buildwhiz.baf2.PhaseApi
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class RecalculatePhaseDuration extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val parameterString = getStreamData(request)
      BWLogger.log(getClass.getName, request.getMethod, s"Parameter-String: $parameterString", request)
      val postData: DynDoc = Document.parse(parameterString)
      val phaseOid = new ObjectId(postData.phase_id[String])
      val phaseActivities = PhaseApi.allActivities(phaseOid)
      val phaseActivityOids = phaseActivities.map(_._id[ObjectId].toString)
      val activityInfo: Seq[DynDoc] = postData.activity_info[Many[Document]]
      val unknownActivityOids = activityInfo.filterNot(ai => phaseActivityOids.contains(ai.activity_id[String]))
      if (unknownActivityOids.nonEmpty) {
        // ToDo:
      }
      val activityOidsWithoutDuration = activityInfo.filterNot(_.has("duration_likely"))
      if (activityOidsWithoutDuration.nonEmpty) {
        // ToDo:
      }
      val logMessage = phaseActivities.map(_.name[String]).mkString("changed durations of: ", ", ", "")
      val result: Document = Map("ok" -> 1,
        "phase_durations" -> Map("optimistic" -> 20, "pessimistic" -> 25, "likely" -> 22),
        "phase_schedule" -> Map("estimated_start" -> "2021-02-15", "estimated_finish" -> "2021-04-10")
      )
      response.getWriter.print(result.toJson())
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