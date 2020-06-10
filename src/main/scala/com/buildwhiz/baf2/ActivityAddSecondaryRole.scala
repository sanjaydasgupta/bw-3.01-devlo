package com.buildwhiz.baf2

import com.buildwhiz.baf2.ActivityApi.teamAssignment
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId
import com.buildwhiz.infra.DynDoc

class ActivityAddSecondaryRole extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      //      if (!ProcessApi.canManage(user._id[ObjectId], theActivity))
      //        throw new IllegalArgumentException("Not permitted")
      val newRole = parameters("role")
      if (!RoleListSecondary.secondaryRoles.contains(newRole))
        throw new IllegalArgumentException(s"Bad role: '$newRole'")

      val activityOid = new ObjectId(parameters("activity_id"))
      if (!ActivityApi.exists(activityOid))
        throw new IllegalArgumentException(s"Bad activity_id: '$activityOid'")

      val optOrganizationOid = parameters.get("organization_id").map(orgId => {
        val orgOid = new ObjectId(orgId)
        if (!OrganizationApi.exists(orgOid))
          throw new IllegalArgumentException(s"Bad organization_id: '$orgId'")
        orgOid
      })

      teamAssignment.roleAdd(activityOid, newRole, optOrganizationOid, userOid)

      val message = s"Added new role '$newRole' to activity $activityOid"
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}