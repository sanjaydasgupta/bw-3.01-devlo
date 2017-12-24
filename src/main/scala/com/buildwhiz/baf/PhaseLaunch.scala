package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines

class PhaseLaunch extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val phaseId = parameters("phase_id")
      val bpmnName = parameters("phase_bpmn_name")
      val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
      val processInstance = rts.startProcessInstanceByKey(bpmnName,
        Map("project_id" -> parameters("project_id"), "phase_id" -> phaseId, "top_level_bpmn" -> bpmnName))
      val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> new ObjectId(phaseId)),
        Map("$set" -> Map("process_instance_id" -> processInstance.getProcessInstanceId)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      response.setContentType("text/plain")
      response.setStatus(HttpServletResponse.SC_OK)
      val thePhase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> new ObjectId(phaseId))).head
      val phaseLog = s"'${thePhase.name[String]}' ($phaseId)"
      BWLogger.audit(getClass.getName, "doPost", s"""Launched phase $phaseLog""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
