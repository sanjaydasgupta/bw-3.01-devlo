package com.buildwhiz.baf

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class ProjectConfigurationFetch extends HttpServlet with HttpUtils {

  private val standardRoleNames = Seq("Quality-Assurance", "Safety-Assurance", "Site-Management")

  private def getRoleCandidates(project: DynDoc): Seq[DynDoc] = {
    val candidates: Seq[DynDoc] = BWMongoDB3.persons.find(Map("first_name" -> Map("$regex" -> "Tester.?")))
    standardRoleNames.zipWithIndex.flatMap(rni => candidates.take(rni._2 + 1).map(c => {
      val name = s"${c.first_name[String]} ${c.last_name[String]}"
      Map("role" -> rni._1, "person_id" -> c._id[ObjectId].toString, "name" -> name)
    }))
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      //val user: DynDoc = getUser(request)
      //if (project.admin_person_id[ObjectId] != user._id[ObjectId])
      //  throw new IllegalArgumentException("not permitted")
      val description = if (project.has("description")) project.description[String] else ""
      val roleNames: Seq[String] = if (project.has("role_names")) {
        project.role_names[Many[String]]
      } else {
        standardRoleNames
      }
      //val pmPerson: DynDoc = BWMongoDB3.persons.find(Map("_id" -> project.admin_person_id[ObjectId])).head
      //val pmName = s"${pmPerson.first_name[String]} ${pmPerson.last_name[String]}"
      //val manager: DynDoc = Map("role" -> "Project-Manager", "person_id" -> pmPerson._id[ObjectId].toString, "name" -> pmName)
      val assignees: Seq[DynDoc] = if (project.has("assigned_roles")) {
        val projectAssignees: Seq[DynDoc] = project.assignees[Many[Document]].map(pa => {
          val assignee: DynDoc = BWMongoDB3.persons.find(Map("_id" -> pa.person_id[ObjectId])).head
          val assigneeName = s"${assignee.first_name[String]} ${assignee.last_name[String]}"
          Map("role" -> pa.role[String], "person_id" -> pa.person_id[ObjectId].toString, "name" -> assigneeName)
        })
        projectAssignees
      } else {
        Seq.empty[DynDoc]
      }
      val projectConfiguration: Document = Map("description" -> description, "role_names" -> roleNames,
        "assigned_roles" -> assignees/*, "role_candidates" -> getRoleCandidates(project)*/)
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
