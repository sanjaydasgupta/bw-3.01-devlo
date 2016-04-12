package com.buildwhiz.tools

import java.util.{Calendar, TimeZone}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{BWLogger, BWMongoDB3, Utils}
import BWMongoDB3._
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines
import org.camunda.bpm.engine.repository.ProcessDefinition
import org.camunda.bpm.engine.runtime.ProcessInstance

import scala.collection.JavaConversions._
import scala.language.implicitConversions

class BrowseOrphans extends HttpServlet with Utils {

  private def orphanProcesses(): Unit = {
    val repositoryService = ProcessEngines.getDefaultProcessEngine.getRepositoryService
    val allProcessDefinitions: Seq[ProcessDefinition] =
      repositoryService.createProcessDefinitionQuery().latestVersion().list()

    val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
    val projects: Seq[DynDoc] = BWMongoDB3.projects.find().toSeq
    val projectIds = projects.map(_._id[ObjectId].toString)
    val query = rts.createProcessInstanceQuery()
    val instances: Seq[ProcessInstance] =
      projectIds.foldLeft(query)((q, id) => q.variableValueNotEquals("project_id", id)).list()
    val definitions: Seq[ProcessDefinition] = instances.map(_.getProcessDefinitionId).
      map(did => allProcessDefinitions.find(_.getId == did)).flatMap(_.toSeq)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    writer.println(s"<html><head><title>Orphans</title></head><body>")
    try {
      writer.println(s"</body></html>")
      response.setContentType("text/html")
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