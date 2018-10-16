package com.buildwhiz.baf

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class ProjectRoleCandidatesFetch extends HttpServlet with HttpUtils {

  private def getRoleCandidates(project: DynDoc, roleName: String): Seq[DynDoc] = {
    val roleRegex = s".*$roleName.*"
    val candidates: Seq[DynDoc] = BWMongoDB3.persons.find(Map("roles" -> Map("$regex" -> roleRegex)))
    val result: Seq[DynDoc] = candidates.map(candidate => {
      val name = s"${candidate.first_name[String]} ${candidate.last_name[String]}"
      Map("person_id" -> candidate._id[ObjectId].toString, "name" -> name)
    })
    result.sortBy(_.name[String])
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val roleName = parameters("role_name")
      val candidates: Seq[DynDoc] = getRoleCandidates(project, roleName)
      response.getWriter.println(candidates.map(d => d.asDoc.toJson).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${candidates.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod,
            s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}
