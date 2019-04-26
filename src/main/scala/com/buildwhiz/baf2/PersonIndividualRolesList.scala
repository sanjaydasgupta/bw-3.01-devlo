package com.buildwhiz.baf2

import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class PersonIndividualRolesList extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    //val parameters = getParameterMap(request)
    try {
      val possibleRoles = PersonIndividualRolesList.possibleIndividualRoles
      response.getWriter.print(possibleRoles.map(ir => "\"%s\"".format(ir)).mkString("[", ",", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${possibleRoles.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object PersonIndividualRolesList {
  val possibleIndividualRoles: Seq[String] = Seq("Principal", "Admin", "Finance", "Contract", "Lead", "Contributor")
}