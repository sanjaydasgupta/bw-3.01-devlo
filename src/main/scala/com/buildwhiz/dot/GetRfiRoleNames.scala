package com.buildwhiz.dot

import com.buildwhiz.api.RestUtils
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.BWLogger
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class GetRfiRoleNames extends HttpServlet with RestUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    val user: DynDoc = getUser(request)
    val projectOid = new ObjectId(parameters("project_id"))
    //ToDo
    //ToDo - obtain roles from BPMN derived data
    //ToDo
    val roles = Seq("Architect", "Mech-Engr", "Elect-Engr", "Proj-Mgr", "Vendor-One")
    val rfiRoleNames = roles.mkString("[", ", ", "]")
    response.getWriter.println(rfiRoleNames)
    response.setContentType("application/json")
    response.setStatus(HttpServletResponse.SC_OK)

    BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK ($rfiRoleNames)", request)
  }

}