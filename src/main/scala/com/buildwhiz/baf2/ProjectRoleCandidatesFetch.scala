package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class ProjectRoleCandidatesFetch extends HttpServlet with HttpUtils {

  private def getRoleCandidates(projectOid: ObjectId): Seq[DynDoc] = {
    val assignments: Seq[DynDoc] = BWMongoDB3.activity_assignments.
        find(Map("project_id" -> projectOid, "person_id" -> Map($exists -> true)))
    val personOids = assignments.map(_.person_id[ObjectId])
    val candidates: Seq[DynDoc] = BWMongoDB3.persons.find(Map("_id" -> Map($in -> personOids)))
    val result: Seq[DynDoc] = candidates.map(candidate => {
      val name = PersonApi.fullName(candidate)
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
      //val roleName = parameters("role_name")
      val candidates: Seq[DynDoc] = getRoleCandidates(projectOid)
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
