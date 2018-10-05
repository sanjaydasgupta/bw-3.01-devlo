package com.buildwhiz.baf

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class PhaseConfigurationFetch extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val description = if (phase.has("description")) phase.description[String] else ""
      val roleNames: Seq[String] = if (phase.has("role_names")) {
        phase.role_names[Many[String]]
      } else {
        ProjectConfigurationFetch.standardRoleNames
      }
      val assignedRoles: Seq[DynDoc] = if (phase.has("assigned_roles")) {
        val phaseAssignees: Seq[DynDoc] = phase.assigned_roles[Many[Document]].map(pa => {
          val assignee: DynDoc = BWMongoDB3.persons.find(Map("_id" -> pa.person_id[ObjectId])).head
          val assigneeName = s"${assignee.first_name[String]} ${assignee.last_name[String]}"
          Map("role_name" -> pa.role_name[String], "person_id" -> pa.person_id[ObjectId].toString,
            "person_name" -> assigneeName)
        })
        phaseAssignees
      } else {
        Seq.empty[DynDoc]
      }
      val phaseConfiguration: Document = Map("description" -> description, "role_names" -> roleNames,
        "assigned_roles" -> assignedRoles)
      response.getWriter.println(phaseConfiguration.toJson)
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
