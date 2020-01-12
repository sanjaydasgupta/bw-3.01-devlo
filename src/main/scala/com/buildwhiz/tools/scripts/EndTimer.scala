package com.buildwhiz.tools.scripts

import java.util.Calendar

import com.buildwhiz.utils.BWLogger
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.camunda.bpm.engine.ProcessEngines

object EndTimer {

  def main(request: HttpServletRequest, response: HttpServletResponse,
      args: Array[String] = Array.empty[String]): Unit = {
    BWLogger.log(getClass.getName, "main()", "ENTRY", request)
    val writer = response.getWriter
    if (args.length >= 2) {
      val processInstanceId = args(0)
      //val processOid = new ObjectId(args(0))
      val timerBpmnId = args(1)
      //val theProcess = ProcessApi.processById(processOid)
      //val timers: Seq[DynDoc] = theProcess.timers[Many[Document]]
      //if (timers.exists(_.bpmn_id[String] == timerBpmnId)) {
        //if (theProcess.has("process_instance_id")) {
          val processEngine = ProcessEngines.getDefaultProcessEngine
          //val processInstanceId = theProcess.process_instance_id[String]
          val managementService = processEngine.getManagementService
          val job = managementService.createJobQuery().processInstanceId(processInstanceId).timers().singleResult()
          if (job != null) {
            writer.println(s"SUCCESS: Found timer '$timerBpmnId' for job '${job.getId}'")
            if (args.length > 2) {
              val calendar = Calendar.getInstance()
              calendar.add(Calendar.MINUTE, 1)
              managementService.setJobDuedate(job.getId, calendar.getTime)
              writer.println(s"SUCCESS: invoked 'setJobDuedate' for 1 minute later")
            } else
              writer.println(s"Add dummy 3rd argument to invoke 'setJobDuedate'")
          } else
            writer.println(s"FAILURE: No job for timer '$timerBpmnId'")
        //} else
        //  writer.println(s"FAILURE: Process has no 'process_instance_id'")
      //} else
      //  writer.println(s"FAILURE: Bad timer-name: '$timerBpmnId'")
      writer.flush()
      response.setContentType("text/plain")
      BWLogger.log(getClass.getName, "main()", "EXIT-OK", request)
    } else {
      val msg = "FAILURE: Usage is 'EndTimer process-instance-id timer-bpmn_id [update]'"
      writer.println(msg)
      writer.flush()
      response.setContentType("text/plain")
      BWLogger.log(getClass.getName, "main()", msg, request)
    }
  }

}
