package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

class ProjectLaunch extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val oid = new ObjectId(parameters("project_id"))
      // instantiate BPMN process (ProjectControl)
      //val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
      //rts.startProcessInstanceByKey("Infra-Project-Control2", Map("project_id" -> parameters("project_id")))
      // update project status and start-timestamp
      val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> oid),
        Map("$set" -> Map("status" -> "running", "timestamps.start" -> System.currentTimeMillis)))
      response.setContentType("text/plain")
      response.getWriter.print(updateResult)
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
