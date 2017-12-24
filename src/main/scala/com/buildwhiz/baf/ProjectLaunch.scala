package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

class ProjectLaunch extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      // instantiate BPMN process (ProjectControl)
      //val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
      //rts.startProcessInstanceByKey("Infra-Project-Control2", Map("project_id" -> parameters("project_id")))
      // update project status and start-timestamp
      val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid),
        Map("$set" -> Map("status" -> "running", "timestamps.start" -> System.currentTimeMillis)))
      response.setContentType("text/plain")
      response.getWriter.print(updateResult)
      response.setStatus(HttpServletResponse.SC_OK)
      val theProject: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val projectLog = s"'${theProject.name[String]}' (${theProject._id[ObjectId]})"
      BWLogger.audit(getClass.getName, "doPost", s"""Launched project $projectLog""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}
