package com.buildwhiz.baf3

import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId
import com.buildwhiz.baf2.PersonApi

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class PersonList extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
//      val skillOption: Option[String] = parameters.get("skill") match {
//        case Some(skill) =>
//          if (RoleListSecondary.secondaryRoles.contains(skill) || skill == "none")
//          //if (RoleListSecondary.secondaryRoles.contains(skill))
//            None
//          else
//            Some(skill)
//        case other => other
//      }
      val skillOption: Option[String] = parameters.get("skill")
      val organisationOidOption = parameters.get("organization_id").map(new ObjectId(_))
      val projectOidOption = parameters.get("project_id").map(new ObjectId(_))
      val phaseOidOption = parameters.get("phase_id").map(new ObjectId(_))
      val processOidOption = parameters.get("process_id").map(new ObjectId(_))
      val activityOidOption = parameters.get("activity_id").map(new ObjectId(_))
      val persons = PersonApi.fetch(None, organisationOidOption, skillOption, projectOidOption, phaseOidOption,
          processOidOption, activityOidOption)
      val personDocuments = persons.map(PersonApi.person2document).sortBy(p => p.getString("name"))
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