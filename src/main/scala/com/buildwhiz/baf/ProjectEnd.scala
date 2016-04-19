package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import BWMongoDB3._
import com.buildwhiz.Utils
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines

import scala.collection.JavaConversions._

class ProjectEnd extends HttpServlet with Utils {

  private def hasActiveProcesses(project_id: String): Boolean = {
    val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
    val children = rts.createProcessInstanceQuery().variableValueEquals("project_id", project_id).list()
    children.nonEmpty
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      //ToDo: Need to check the status of child objects (phases, ... actions)
      val project_id = parameters("project_id")
      if (hasActiveProcesses(project_id))
        throw new IllegalArgumentException("Project has active children")
      val projectOid = new ObjectId(project_id)
      val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid),
        Map("$set" -> Map("status" -> "ended", "timestamps.end" -> System.currentTimeMillis)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
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
