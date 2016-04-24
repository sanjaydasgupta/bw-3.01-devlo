package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.HttpUtils
import com.buildwhiz.infra.BWLogger
import org.camunda.bpm.engine.ProcessEngines
import org.camunda.bpm.engine.repository.ProcessDefinition

import scala.collection.JavaConversions._

class PhaseBpmnXml extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val bpmnFileName = parameters("bpmn_name").replaceAll(" ", "-")
      val repositoryService = ProcessEngines.getDefaultProcessEngine.getRepositoryService
      val allProcessDefinitions: Seq[ProcessDefinition] =
        repositoryService.createProcessDefinitionQuery().latestVersion().list()
      val processDefinition = allProcessDefinitions.find(_.getKey == bpmnFileName).head
      val processModelStream = repositoryService.getProcessModel(processDefinition.getId)
      val buffer = new Array[Byte](4096)
      val printStream = response.getOutputStream
      def copyModelToOutput(): Unit = {
        val len = processModelStream.read(buffer)
        if (len > 0) {
          printStream.write(buffer, 0, len)
          copyModelToOutput()
        }
      }
      copyModelToOutput()
      response.setContentType("application/xml")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
