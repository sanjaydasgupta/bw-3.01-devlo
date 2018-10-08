package com.buildwhiz.baf

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class PhaseRoleCandidatesFetch extends HttpServlet with HttpUtils {

  private def getRoleCandidates(phase: DynDoc): Map[String, Seq[DynDoc]] = {
    val nameRegex = "Prabhas|Sanjay|Gouri|Tester."
    val candidates: Seq[DynDoc] = BWMongoDB3.persons.find(Map("first_name" -> Map("$regex" -> nameRegex))).
        sortBy(_.first_name[String])
    ProjectConfigurationFetch.standardRoleNames.zipWithIndex.map(roleWithIndex =>
      roleWithIndex._1 -> candidates.take(roleWithIndex._2 * 2 + 1).map(c => {
        val name = s"${c.first_name[String]} ${c.last_name[String]}"
        val candidateInfo: DynDoc = Map("person_id" -> c._id[ObjectId].toString, "name" -> name)
        candidateInfo
      })).toMap
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val roleName = parameters("role_name")
      val candidates: Seq[DynDoc] = getRoleCandidates(phase).getOrElse(roleName, Seq.empty[DynDoc])
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
