package com.buildwhiz.baf2

import com.buildwhiz.utils.{BWLogger, HttpUtils}
import com.buildwhiz.infra.DynDoc
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class ActivityAddPersonAndIndivRole extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      //      if (!ProcessApi.canManage(user._id[ObjectId], theActivity))
      //        throw new IllegalArgumentException("Not permitted")
      val activityOids = parameters("activity_id").split(",").map(id => new ObjectId(id.trim))
      val badOids = activityOids.filter(!ActivityApi.exists(_))
      if (badOids.nonEmpty)
        throw new IllegalArgumentException(s"""Bad activity_id values: '${badOids.mkString(", ")}'""")

      val theRole = parameters("role")
      //if (theRole != theActivity.role[String] && !assignments.exists(_.role[String] == theRole))
      //  throw new IllegalArgumentException(s"Bad role '$theRole'")

      val organizationOid = new ObjectId(parameters("organization_id"))
      if (!OrganizationApi.exists(organizationOid))
        throw new IllegalArgumentException(s"Bad organization_id '$organizationOid'")

      val personOid = new ObjectId(parameters("person_id"))
      if (!PersonApi.exists(personOid))
        throw new IllegalArgumentException(s"Bad person_id '$personOid'")

      val individualRole = parameters("individual_role").split(",").map(_.trim).filter(_.nonEmpty)
      val badRoles = individualRole.filterNot(PersonApi.possibleIndividualRoles.contains(_))
      if (badRoles.nonEmpty)
        throw new IllegalArgumentException(s"""Bad individual_role(s): ${badRoles.mkString(", ")}""")

      val documentAccess = parameters("document_access").split(",").map(_.trim).filter(_.nonEmpty)

      activityOids.foreach(
        ActivityApi.teamAssignment.personAdd(_, theRole, organizationOid, personOid, individualRole,
            documentAccess, userOid)
      )

      val message = s"""Added person $personOid in role '$theRole' to activities ${activityOids.mkString(", ")}"""
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}