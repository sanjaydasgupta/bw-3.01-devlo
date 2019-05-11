package com.buildwhiz.baf2

import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class PersonList extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    val skillOption: Option[String] = parameters.get("skill") match {
      case Some(skill) =>
        if (RoleListSecondary.secondaryRoles.contains(skill))
          None
        else
          Some(skill)
      case other => other
    }
    val organisationOidOption = parameters.get("organization_id").map(new ObjectId(_))
    try {
      val persons = PersonApi.fetch(None, organisationOidOption, skillOption)
      val personDocuments = persons.map(PersonApi.person2document)
      response.getWriter.print(personDocuments.map(bson2json).mkString("[", ",", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${persons.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}