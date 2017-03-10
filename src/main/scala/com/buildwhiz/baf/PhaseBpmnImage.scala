package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.utils.{BWLogger, BpmnUtils, HttpUtils}
import org.camunda.bpm.engine.ProcessEngines
import org.camunda.bpm.engine.repository.{Deployment, ProcessDefinition}

import scala.collection.JavaConverters._
import scala.util.{Success, Try}

class PhaseBpmnImage extends HttpServlet with HttpUtils with BpmnUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val bpmnFileName = parameters("bpmn_name").replaceAll(" ", "-")
      val repositoryService = ProcessEngines.getDefaultProcessEngine.getRepositoryService

      val allProcDefs: Seq[ProcessDefinition] = repositoryService.createProcessDefinitionQuery().list().asScala
      BWLogger.log(getClass.getName, "doGet", s"""allProcessDefinitions: ${allProcDefs.mkString(", ")}""", request)
      val processDefinitions = allProcDefs.filter(_.getResourceName.contains(bpmnFileName))
      BWLogger.log(getClass.getName, "doGet", s"""processDefinitions: ${processDefinitions.mkString(", ")}""", request)
      val processIds = processDefinitions.map(_.getId)
      BWLogger.log(getClass.getName, "doGet", s"""processIds: ${processIds.mkString(", ")}""", request)
      val diagramStream = processIds.map(pid => Try(repositoryService.getProcessDiagram(pid))).
          find(t => t.isSuccess && t.get != null)
      BWLogger.log(getClass.getName, "doGet", s"""diagramStream: ${diagramStream.mkString(", ")}""", request)
      val buffer = new Array[Byte](4096)
      val printStream = response.getOutputStream
      def copyDiagramToOutput(): Unit = {
        val len = diagramStream match {
          case Some(Success(is)) => is.read(buffer)
          case _ => 0
        }
        if (len > 0) {
          printStream.write(buffer, 0, len)
          copyDiagramToOutput()
        }
      }
      copyDiagramToOutput()
      response.setContentType("image/png")
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
