package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines

class TimerDurationSet extends HttpServlet with HttpUtils with DateTimeUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
      val (duration, bpmnName) = (parameters("duration"), parameters("bpmn_name"))
      val (timerId, timerName) = (parameters.get("timer_id"), parameters.get("timer_name"))
      TimerDurationSet.set(request, response, phaseOid, timerId, timerName, bpmnName, duration)
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object TimerDurationSet extends DateTimeUtils {

  private def formatDuration(d: String): String = {
    val parts = d.split(":").map(_.toInt) match {
      case arr3 @ Array(_, _, _) => arr3
      case arr2 @ Array(_, _) => 0 +: arr2
      case arr1 @ Array(_) => 0 +: 0 +: arr1
    }
    parts.map(p => f"$p%02d").mkString(":")
  }

  def set(request: HttpServletRequest, response: HttpServletResponse, phaseOid: ObjectId, timerId: Option[String],
          timerName: Option[String], bpmnName: String, inDuration: String): Unit = {
    val thePhase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
    val timers: Seq[DynDoc] = thePhase.timers[Many[Document]]
    val timerIdx: Int = (timerId, timerName) match {
      case (Some(tid), _) => timers.indexWhere(t => t.bpmn_id[String] == tid &&
        t.bpmn_name[String] == bpmnName)
      case (_, Some(tname)) =>
        timers.indexWhere(t => t.name[String].replace("\\s+", "") == tname.replace("\\s+", "") &&
          t.bpmn_name[String] == bpmnName)
      case _ => throw new IllegalArgumentException("Timer id or name not provided")
    }
    if (!inDuration.matches("(?:(?:\\d{1,2}\\:)?\\d{1,2}\\:)?\\d{1,2}"))
      throw new IllegalArgumentException("Bad duration format")
    val duration = formatDuration(inDuration)
    if (thePhase.status[String] != "ended") {
      if (thePhase.has("process_instance_id")) {
        val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
        val processInstanceId = thePhase.process_instance_id[String]
        rts.setVariable(processInstanceId, timers(timerIdx).variable[String], duration2iso(duration))
      }
      val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
        Map("$set" -> Map(s"timers.$timerIdx.duration" -> duration)))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      else {
        val topLevelBpmn = thePhase.bpmn_name[String]
        PhaseBpmnTraverse.scheduleBpmnElements(topLevelBpmn, phaseOid, request, response)
      }
      val timerLog = s"'${timers(timerIdx).name[String]}'"
      BWLogger.audit(getClass.getName, "doPost", s"""Set duration of timer $timerLog""", request)
    } else
      BWLogger.log(getClass.getName, "doPost", "EXIT-OK (Process already ended, no changes)", request)
  }
}
