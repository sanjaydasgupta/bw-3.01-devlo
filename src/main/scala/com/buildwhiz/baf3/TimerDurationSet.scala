package com.buildwhiz.baf3

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines
import com.buildwhiz.baf2.{PersonApi, PhaseApi}

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class TimerDurationSet extends HttpServlet with HttpUtils with DateTimeUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
      val thePhase = PhaseApi.phaseById(phaseOid)
      val user: DynDoc = getPersona(request)
      if (!PhaseApi.canManage(user._id[ObjectId], thePhase) && !PersonApi.isBuildWhizAdmin(Right(user)))
        throw new IllegalArgumentException("Not permitted")
      val (duration, bpmnName) = (parameters("duration"), parameters("bpmn_name"))
      val (timerId, timerName) = (parameters.get("timer_id"), parameters.get("timer_name"))
      val theProcess: DynDoc = PhaseApi.allProcesses2(phaseOid).headOption match {
        case Some(p) => p
        case None => throw new IllegalArgumentException("Phase has no processes")
      }
      TimerDurationSet.set(request, theProcess, timerId, timerName, bpmnName, duration)
      response.getWriter.print(successJson())
      response.setContentType("application/json")
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
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

  def set(request: HttpServletRequest, theProcess: DynDoc, timerId: Option[String], timerName: Option[String],
          bpmnName: String, inDuration: String): Unit = {
    //val theProcess: DynDoc = ProcessApi.processById(processOid)
    val timers: Seq[DynDoc] = theProcess.timers[Many[Document]]
    val timerIdx: Int = (timerId, timerName) match {
      case (Some(tid), _) => timers.indexWhere(t => t.bpmn_id[String] == tid &&
        t.bpmn_name[String] == bpmnName)
      case (_, Some(tName)) =>
        timers.indexWhere(t => t.name[String].replace("\\s+", "") == tName.replace("\\s+", "") &&
          t.bpmn_name[String] == bpmnName)
      case _ => throw new IllegalArgumentException("Timer id or name not provided")
    }
    if (!inDuration.matches("(?:(?:\\d{1,4}\\:)?\\d{1,2}\\:)?\\d{1,2}"))
      throw new IllegalArgumentException("Bad duration format")
    val duration = formatDuration(inDuration)
    if (theProcess.status[String] != "ended") {
      theProcess.bpmn_timestamps[Many[Document]].find(_.name[String] == bpmnName) match {
        case Some(ts) => if (ts.has("process_instance_id")) {
          val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
          val processInstanceId = theProcess.process_instance_id[String]
          rts.setVariable(processInstanceId, timers(timerIdx).variable[String], duration2iso(duration))
        }
        case None =>
      }
      val processOid = theProcess._id[ObjectId]
      val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> processOid),
        Map("$set" -> Map(s"timers.$timerIdx.duration" -> duration)))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      else {
        //val topLevelBpmn = theProcess.bpmn_name[String]
        //   TO BE COMPLETED
        //ProcessBpmnTraverse.scheduleBpmnElements(topLevelBpmn, processOid, request)
      }
      val timerLog = s"'${timers(timerIdx).name[String]}'"
      BWLogger.audit(getClass.getName, request.getMethod, s"""Set duration of timer '$timerLog' to '$duration'""", request)
    } else
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK (Process already ended, no changes)", request)
  }
}
