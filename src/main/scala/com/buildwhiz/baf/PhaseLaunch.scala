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
      val user: DynDoc = getUser(request)
      val phaseId = parameters("phase_id")
      val phaseOid = new ObjectId(phaseId)
      val thePhase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      if (user._id[ObjectId] != thePhase.admin_person_id[ObjectId])
        throw new IllegalArgumentException("Not permitted")
      val bpmnName = thePhase.bpmn_name[String]
      val project: DynDoc = BWMongoDB3.projects.find(Map("phase_ids" -> phaseOid)).head
      val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
      val processInstance = rts.startProcessInstanceByKey(bpmnName,
        Map("project_id" -> project._id[ObjectId].toString, "phase_id" -> phaseId, "top_level_bpmn" -> bpmnName))
      val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
        Map("$set" -> Map("process_instance_id" -> processInstance.getProcessInstanceId)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      response.setContentType("text/plain")
      response.setStatus(HttpServletResponse.SC_OK)
      val phaseLogMessage = s"Launched phase '${thePhase.name[String]}' ($phaseId)"
      BWLogger.audit(getClass.getName, "doPost", phaseLogMessage, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
