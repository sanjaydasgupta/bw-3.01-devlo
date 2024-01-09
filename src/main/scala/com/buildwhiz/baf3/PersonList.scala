package com.buildwhiz.baf3

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId
import scala.jdk.CollectionConverters._

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class PersonList extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val t0 = System.currentTimeMillis()
    val parameters = getParameterMap(request)
    try {
      val skillOption: Option[String] = parameters.get("skill")
      val organisationOidOption = parameters.get("organization_id").map(new ObjectId(_))
      val projectOidOption = parameters.get("project_id").map(new ObjectId(_))
      val phaseOidOption = parameters.get("phase_id").map(new ObjectId(_))
      val processOidOption = parameters.get("process_id").map(new ObjectId(_))
      val activityOidOption = parameters.get("activity_id").map(new ObjectId(_))
      val persons = PersonApi.fetch3(None, organisationOidOption, skillOption, projectOidOption, phaseOidOption,
        processOidOption, activityOidOption)
      val personDocuments = persons.map(p => {
        val pd: DynDoc = PersonApi.person2document(p)
        pd._id = pd._id[ObjectId].toString
        pd.asDoc
      }).sortBy(p => p.getString("name")).asJava
      val user: DynDoc = getPersona(request)
      val isAdmin = PersonApi.isBuildWhizAdmin(Right(user))
      val hostName = getHostName(request)
      val menuItems = uiContextSelectedManaged(request) match {
        case None => displayedMenuItems(isAdmin, request, starting = true)
        case Some((selected, managed)) => displayedMenuItems(isAdmin, request, managed, !selected)
      }
      val result = new Document("person_list", personDocuments).append("menu_items", menuItems)
      response.getWriter.print(result.toJson)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      val delay = System.currentTimeMillis() - t0
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${persons.length}, time: $delay ms)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}