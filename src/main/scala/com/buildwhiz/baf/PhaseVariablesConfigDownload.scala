package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

class PhaseVariablesConfigDownload extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val phaseOid = new ObjectId(parameters("phase_id"))
      val bpmnName = parameters("bpmn_name")
      val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val projectName = project.name[String]
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val phaseName = phase.name[String]
      val variables: Seq[DynDoc] = phase.variables[Many[Document]].filter(_.bpmn_name[String] == bpmnName)
      val timers: Seq[DynDoc] = phase.timers[Many[Document]].filter(_.bpmn_name[String] == bpmnName)
      val outputStream = response.getOutputStream
      outputStream.println("Variable/Timer, Name, Type, Value/Duration")
      for (variable <- variables) {
        val variableName = variable.label[String]
        val vType = variable.`type`[String]
        val value = variable.value[AnyRef].toString
        val buffer = s"VARIABLE, $variableName ($projectName/$phaseName), $vType, $value"
        outputStream.println(buffer)
      }
      for (timer <- timers) {
        val timerName = timer.name[String]
        val duration = timer.duration[String]
        val buffer = s"TIMER, $timerName ($projectName/$phaseName), DURATION, $duration"
        outputStream.println(buffer)
      }
      response.setContentType("text/csv")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", s"EXIT-OK (${variables.length} variables, ${timers.length} timers)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
