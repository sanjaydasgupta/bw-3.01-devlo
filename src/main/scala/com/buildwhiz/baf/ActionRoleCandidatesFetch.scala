package com.buildwhiz.baf

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class ActionRoleCandidatesFetch extends HttpServlet with HttpUtils {

  private def getRoleCandidates(activity: DynDoc, roleName: String): Seq[DynDoc] = {
    val candidates: Seq[DynDoc] = BWMongoDB3.persons.find(Map("$or" ->
      Seq(Map("roles" -> Map("$regex" -> ".*-Assurance.*")), Map("first_name" -> Map("$regex" -> "Tester.*")))))
    val result: Seq[DynDoc] = candidates.map(candidate => {
      val name = s"${candidate.first_name[String]} ${candidate.last_name[String]}"
      Map("person_id" -> candidate._id[ObjectId].toString, "name" -> name)
    })
    result.sortBy(_.name[String])
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    //
    // ToDo: strategy needed
    //
    try {
      val activityOid = new ObjectId(parameters("activity_id"))
      val activity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).head
      //val actionType = parameters("action_type")
      //val resultRoles = ActionRolesFetch.roles(activityOid, actionType)
      val roleName = parameters("role_name")
      val candidates: Seq[DynDoc] = getRoleCandidates(activity, roleName)
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
