package com.buildwhiz.baf

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class PhaseRoleCandidatesFetch extends HttpServlet with HttpUtils {

  private def getRoleCandidates(phase: DynDoc, roleName: String): Seq[DynDoc] = {
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
      val phaseOid = new ObjectId(parameters("phase_id"))
      val phase: DynDoc = BWMongoDB3.processes.find(Map("_id" -> phaseOid)).head
      val roleName = parameters("role_name")
      val candidates: Seq[DynDoc] = getRoleCandidates(phase, roleName)
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
