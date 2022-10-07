package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, BpmnUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class ProjectEnd extends HttpServlet with HttpUtils with BpmnUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val project_id = parameters("project_id")
      val projectOid = new ObjectId(project_id)
      val theProject: DynDoc = ProjectApi.projectById(projectOid)

      if (theProject.status[String] == "ended")
        throw new IllegalArgumentException(s"Project '${theProject.name[String]}' has already ended")
      if (ProjectApi.isActive(theProject))
        throw new IllegalArgumentException(s"Project '${theProject.name[String]}' has active children")

      val user: DynDoc = getUser(request)

      if (user._id[ObjectId] != theProject.admin_person_id[ObjectId])
        throw new IllegalArgumentException("Not permitted")

      val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid),
        Map("$set" -> Map("status" -> "ended", "timestamps.end" -> System.currentTimeMillis)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      response.setStatus(HttpServletResponse.SC_OK)
      val projectLog = s"'${theProject.name[String]}' (${theProject._id[ObjectId]})"
      BWLogger.audit(getClass.getName, request.getMethod, s"""Ended project $projectLog""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
