package com.buildwhiz.obsolete

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.HttpUtils
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines

class PhaseLaunch extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      // resume BPMN project top-level process (ProjectControl)
      val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
      rts.correlateMessage("Phase-Launch", Map("project_id" -> parameters("project_id")),
        Map("phase_bpmn_name" -> parameters("phase_bpmn_name"), "phase_id" -> parameters("phase_id")))
      // restore project status back to 'running' (from waiting)
      BWMongoDB3.projects.updateOne(Map("_id" -> new ObjectId(parameters("project_id"))),
        Map("$set" -> Map("status" -> "running")))
      response.setContentType("text/plain")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doPost", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
