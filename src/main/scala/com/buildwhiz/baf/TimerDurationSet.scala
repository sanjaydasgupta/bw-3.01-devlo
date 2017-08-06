package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

class TimerDurationSet extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    def formatDuration(d: String): String = {
      val parts = d.split(":").map(_.toInt) match {
        case arr3 @ Array(_, _, _) => arr3
        case arr2 @ Array(_, _) => 0 +: arr2
        case arr1 @ Array(_) => 0 +: 0 +: arr1
      }
      parts.map(p => f"$p%02d").mkString(":")
    }
    try {
      val duration = parameters("duration")
      if (!duration.matches("(?:(?:\\d{1,2}\\:)?\\d{1,2}\\:)?\\d{1,2}"))
        throw new IllegalArgumentException("Bad duration format")
      val phaseOid = new ObjectId(parameters("phase_id"))
      val thePhase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val bpmnName = parameters("bpmn_name")
      val timers: Seq[DynDoc] = thePhase.timers[Many[Document]]
      val timerIdx: Int = (parameters.get("timer_id"), parameters.get("timer_name")) match {
        case (Some(timerId), _) => timers.indexWhere(t => t.bpmn_id[String] == timerId &&
          t.bpmn_name[String] == bpmnName)
        case (_, Some(timerName)) =>
          timers.indexWhere(t => t.name[String].replace("\\s+", "") == timerName.replace("\\s+", "") &&
            t.bpmn_name[String] == bpmnName)
        case _ => throw new IllegalArgumentException("Timer id or name not provided")
      }
      val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
        Map("$set" -> Map(s"timers.$timerIdx.duration" -> formatDuration(duration))))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      else {
        val topLevelBpmn = thePhase.bpmn_name[String]
        PhaseBpmnTraverse.scheduleBpmnElements(topLevelBpmn, phaseOid, request, response)
      }
      response.setStatus(HttpServletResponse.SC_OK)
      val timerLog = s"'${timers(timerIdx).name[String]}'"
      BWLogger.audit(getClass.getName, "doPost", s"""Set duration of timer $timerLog""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
