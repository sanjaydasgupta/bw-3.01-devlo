package com.buildwhiz.baf2

import com.buildwhiz.baf2.ActivityApi.teamAssignment
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class ActivityAssignmentDelete extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      //      if (!ProcessApi.canManage(user._id[ObjectId], theActivity))
      //        throw new IllegalArgumentException("Not permitted")

      val assignmentOid = new ObjectId(parameters("assignment_id"))
      if (BWMongoDB3.activity_assignments.countDocuments(Map("_id" -> assignmentOid)) == 0)
        throw new IllegalArgumentException(s"Bad assignment_id: '$assignmentOid'")

      teamAssignment.deleteAssignment(assignmentOid, userOid)

      val message = s"Deleted activity assignment $assignmentOid"
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}