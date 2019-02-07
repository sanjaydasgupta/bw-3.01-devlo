package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines

class ProcessLaunch extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val processId = parameters("process_id")
      val processOid = new ObjectId(processId)
      val theProcess: DynDoc = BWMongoDB3.processes.find(Map("_id" -> processOid)).head
      if (user._id[ObjectId] != theProcess.admin_person_id[ObjectId])
        throw new IllegalArgumentException("Not permitted")
      val bpmnName = theProcess.bpmn_name[String]
      val parentPhase: DynDoc = ProcessApi.parentPhase(processOid)
      val parentProject: DynDoc = PhaseApi.parentProject(parentPhase._id[ObjectId])
      val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
      val processInstance = rts.startProcessInstanceByKey(bpmnName,
        Map("project_id" -> parentProject._id[ObjectId].toString, "process_id" -> processId, "top_level_bpmn" -> bpmnName))
      val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> processOid),
        Map("$set" -> Map("process_instance_id" -> processInstance.getProcessInstanceId)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      response.setContentType("text/plain")
      response.setStatus(HttpServletResponse.SC_OK)
      val phaseLogMessage = s"Launched phase '${theProcess.name[String]}' ($processId)"
      BWLogger.audit(getClass.getName, "doPost", phaseLogMessage, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
