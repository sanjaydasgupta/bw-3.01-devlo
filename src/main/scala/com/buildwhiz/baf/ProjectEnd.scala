package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, BpmnUtils, HttpUtils}
import org.bson.types.ObjectId

class ProjectEnd extends HttpServlet with HttpUtils with BpmnUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      //
      //ToDo: Need to check the status of child objects (phases, ... actions)
      //
      val user: DynDoc = getUser(request)
      val project_id = parameters("project_id")
      val projectOid = new ObjectId(project_id)
      val theProject: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      if (user._id[ObjectId] != theProject.admin_person_id[ObjectId])
        throw new IllegalArgumentException("Not permitted")
      if (hasActiveProcesses(project_id))
        throw new IllegalArgumentException("Project has active children")
      val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid),
        Map("$set" -> Map("status" -> "ended", "timestamps.end" -> System.currentTimeMillis)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      response.setStatus(HttpServletResponse.SC_OK)
      val projectLog = s"'${theProject.name[String]}' (${theProject._id[ObjectId]})"
      BWLogger.audit(getClass.getName, "doPost", s"""Ended project $projectLog""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
