package com.buildwhiz.baf2

import com.buildwhiz.api.RestUtils
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.BWLogger
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class RfiDestinations extends HttpServlet with RestUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    //val user: DynDoc = getUser(request)
    //val projectOid = new ObjectId(parameters("project_id"))
    //ToDo - obtain roles from BPMN derived data
    val roles = Seq("Document-Author", "Project-Manager")
    val rfiRoleNames = roles.mkString("[\"", "\", \"", "\"]")
    response.getWriter.println(rfiRoleNames)
    response.setContentType("application/json")
    response.setStatus(HttpServletResponse.SC_OK)

    BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK ($rfiRoleNames)", request)
  }

}