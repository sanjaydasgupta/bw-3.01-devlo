package com.buildwhiz.baf

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class ProjectRoleCandidatesFetch extends HttpServlet with HttpUtils {

  private def getRoleCandidates(project: DynDoc): Map[String, Seq[DynDoc]] = {
    val candidates: Seq[DynDoc] = BWMongoDB3.persons.find(Map("first_name" -> Map("$regex" -> "Tester.?")))
    ProjectConfigurationFetch.standardRoleNames.zipWithIndex.map(roleWithIndex =>
      roleWithIndex._1 -> candidates.take(roleWithIndex._2 + 1).map(c => {
        val name = s"${c.first_name[String]} ${c.last_name[String]}"
        val candidateInfo: DynDoc = Map("person_id" -> c._id[ObjectId].toString, "name" -> name)
        candidateInfo
      })).toMap
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val roleName = parameters("role_name")
      val candidates: Seq[DynDoc] = getRoleCandidates(project).getOrElse(roleName, Seq.empty[DynDoc])
      response.getWriter.println(candidates.map(d => d.asDoc.toJson).mkString("[", ", ", "]"))
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
