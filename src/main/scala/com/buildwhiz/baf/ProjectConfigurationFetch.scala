package com.buildwhiz.baf

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class ProjectConfigurationFetch extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val description = if (project.has("description")) project.description[String] else ""
      val roleNames: Seq[String] = if (project.has("role_names")) {
        project.role_names[Many[String]]
      } else {
        ProjectConfigurationFetch.standardRoleNames
      }
      val assignedRoles: Seq[DynDoc] = if (project.has("assigned_roles")) {
        val projectAssignees: Seq[DynDoc] = project.assigned_roles[Many[Document]].map(pa => {
          val assignee: DynDoc = BWMongoDB3.persons.find(Map("_id" -> pa.person_id[ObjectId])).head
          val assigneeName = PersonApi.fullName(assignee)
          Map("role_name" -> pa.role_name[String], "person_id" -> pa.person_id[ObjectId].toString,
            "person_name" -> assigneeName)
        })
        projectAssignees
      } else {
        Seq.empty[DynDoc]
      }
      val projectConfiguration: Document = Map("description" -> description, "role_names" -> roleNames,
        "assigned_roles" -> assignedRoles/*, "role_candidates" -> getRoleCandidates(project)*/)
      response.getWriter.println(projectConfiguration.toJson)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod,
            s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}

object ProjectConfigurationFetch {

  val standardRoleNames: Seq[String] = Seq("Quality-Assurance", "Safety-Assurance", "Site-Management",
    "Contributor", "Testing")

}