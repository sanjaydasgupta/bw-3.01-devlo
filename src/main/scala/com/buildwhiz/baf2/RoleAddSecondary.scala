package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class RoleAddSecondary extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      //      val user: DynDoc = getUser(request)
      //      if (!ProcessApi.canManage(user._id[ObjectId], theActivity))
      //        throw new IllegalArgumentException("Not permitted")
      val activityOid = new ObjectId(parameters("activity_id"))
      val theActivity = ActivityApi.activityById(activityOid)
      val assignments: Seq[DynDoc] = if (theActivity.has("assignments")) {
        theActivity.assignments[Many[Document]]
      } else {
        Seq.empty[DynDoc]
      }
      val newRole = parameters("role")
      if (!RoleListSecondary.secondaryRoles.contains(newRole))
        throw new IllegalArgumentException(s"Bad role: '$newRole'")
      if (assignments.exists(_.role[String] == newRole))
        throw new IllegalArgumentException(s"Role '$newRole' already exists")
      val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
        Map("$push" -> Map("assignments" -> Map("role" -> newRole))))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}