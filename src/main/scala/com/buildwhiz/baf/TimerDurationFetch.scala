package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

class TimerDurationFetch extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
      val thePhase: DynDoc = BWMongoDB3.processes.find(Map("_id" -> phaseOid)).head
      val bpmnName = parameters("bpmn_name")
      val timersInBpmn: Seq[DynDoc] = thePhase.timers[Many[Document]].filter(t => t.bpmn_name[String] == bpmnName)
      val theTimer: DynDoc = (parameters.get("timer_id"), parameters.get("timer_name")) match {
        case (Some(timerId), _) => timersInBpmn.filter(_.bpmn_id[String] == timerId).head
        case (_, Some(timerName)) =>
          timersInBpmn.filter(_.name[String].replace("\\s+", "") == timerName.replace("\\s+", "")).head
        case _ => throw new IllegalArgumentException("Timer id or name not provided")
      }
      val duration = theTimer.duration[String]
      response.getWriter.print(duration)
      response.flushBuffer()
      response.setContentType("text/plain")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK ($duration)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
