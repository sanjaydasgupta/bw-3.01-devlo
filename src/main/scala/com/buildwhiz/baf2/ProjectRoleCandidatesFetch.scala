package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class ProjectRoleCandidatesFetch extends HttpServlet with HttpUtils {

  private def getRoleCandidates(projectOid: ObjectId, roleName: String): Seq[DynDoc] = {
    val activities = ProjectApi.allActivities(projectOid)
    val assignments = activities.flatMap(activity => ActivityApi.teamAssignment.list(activity._id[ObjectId]))
    val personOids = assignments.filter(_.has("person_id")).map(_.person_id[ObjectId])
    val candidates: Seq[DynDoc] = BWMongoDB3.persons.find(Map("person_id" -> Map($in -> personOids),
        "roles" -> Map($regex -> s".*$roleName.*")))
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
      if (!ProjectApi.exists(projectOid))
        throw new IllegalArgumentException(s"Bad project_id: '$projectOid'")
      val roleName = parameters("role_name")
      val candidates: Seq[DynDoc] = getRoleCandidates(projectOid, roleName)
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
