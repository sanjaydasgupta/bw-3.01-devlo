package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3, Utils}
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines

class PhaseLaunch extends HttpServlet with Utils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val phaseId = parameters("phase_id")
      val bpmnName = parameters("phase_bpmn_name")
      val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
      rts.startProcessInstanceByKey(bpmnName,
        Map("project_id" -> parameters("project_id"), "phase_id" -> phaseId, "top_level_bpmn" -> bpmnName))
//      val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> new ObjectId(phaseId)),
//        Map("$set" -> Map("status" -> "running", "timestamps.start" -> System.currentTimeMillis),
//          "$inc" -> Map("active_bpmn_count" -> 1)))
//      if (updateResult.getModifiedCount == 0)
//        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
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
