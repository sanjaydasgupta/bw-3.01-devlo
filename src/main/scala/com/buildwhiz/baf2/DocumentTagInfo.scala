package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.utils.{BWLogger, BpmnUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId
import org.bson.Document

class DocumentTagInfo extends HttpServlet with HttpUtils with BpmnUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val project: DynDoc = ProjectApi.projectById(projectOid)
      val systemTags = ProjectApi.documentTags(project)

      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      val freshUserRecord = PersonApi.personById(userOid)
      val userTags = PersonApi.documentTags(freshUserRecord)

      val isSystemUser = ProjectApi.canManage(userOid, project)

      val returnValue = new Document("is_system_user", isSystemUser).append("user_tags", userTags).
          append("system_tags", systemTags)

      response.getWriter.print(bson2json(returnValue))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      val logMessage = s"EXIT-OK (is-system-user: $isSystemUser, #system-tags: ${systemTags.length}, " +
          s"#user-tags: ${userTags.length})"
      BWLogger.log(getClass.getName, request.getMethod, logMessage, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}