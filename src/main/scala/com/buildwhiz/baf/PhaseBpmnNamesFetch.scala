package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.Utils
import com.buildwhiz.infra.BWLogger
import org.camunda.bpm.engine.ProcessEngines
import org.camunda.bpm.engine.repository.Resource

import scala.collection.JavaConversions._
import scala.collection.mutable

class PhaseBpmnNamesFetch extends HttpServlet with Utils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val processEngine = ProcessEngines.getDefaultProcessEngine
      val managementService = processEngine.getManagementService
      val deployments: mutable.Set[String] = managementService.getRegisteredDeployments
      val repositoryService = processEngine.getRepositoryService
      val prefix = "1.01/Phase-"
      val bpmnResources: Seq[Resource] = deployments.flatMap(repositoryService.getDeploymentResources).
        filter(_.getName.endsWith(".bpmn")).toSeq
      val phaseNames: Seq[String] = bpmnResources.map(_.getName).filter(_.matches(s"$prefix.+\\.bpmn")).
        map(s => s""""${s.substring(prefix.length, s.length - 5)}"""").distinct
      writer.print(phaseNames.sorted.mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

}