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
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val projectOid = new ObjectId(parameters("project_id"))
      val theProject: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      if (user._id[ObjectId] != theProject.admin_person_id[ObjectId])
        throw new IllegalArgumentException("Not permitted")
      val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid, "status" -> "defined"),
        Map("$set" -> Map("status" -> "running", "timestamps.start" -> System.currentTimeMillis)))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      response.setStatus(HttpServletResponse.SC_OK)
      val logMessage = s"Launched project '${theProject.name[String]}' (${theProject._id[ObjectId]})"
      BWLogger.audit(getClass.getName, request.getMethod, logMessage, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}
