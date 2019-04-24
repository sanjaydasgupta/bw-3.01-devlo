package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class ActivityAssignOrganization extends HttpServlet with HttpUtils {

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

      val theRole = parameters("role")
      if (theRole != theActivity.role[String] && !assignments.exists(_.role[String] == theRole))
        throw new IllegalArgumentException(s"Bad role '$theRole'")

      val organizationOid = new ObjectId(parameters("organization_id"))
      if (!OrganizationApi.exists(organizationOid))
        throw new IllegalArgumentException(s"Bad organization_id '$organizationOid'")

//      if (!assignments.exists(assignment => assignment.role[String] == theRole &&
//        assignment.organization_id[ObjectId] == organizationOid))
//        throw new IllegalArgumentException(s"Bad organization_id '$organizationOid'")

      val newRecord = Map("role" -> theRole, "organization_id" -> organizationOid)

      val existingRecords = assignments.
          filter(assignment => assignment.role[String] == theRole && assignment.has("organization_id"))
      val existingCount = existingRecords.length

      if (existingCount > 0) {
        val deleteResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
            Map("$pull" -> Map("assignments" -> Map("role" -> theRole, "organization_id" -> Map("$exists" -> true)))))
        if (deleteResult.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB error: $deleteResult")
        val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
          Map("$push" -> Map("assignments" -> newRecord)))
        if (updateResult.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB error: $updateResult")
        val message = s"Replaced $existingCount record(s) with (Activity $activityOid, Role $theRole, Organization $organizationOid)"
        BWLogger.audit(getClass.getName, request.getMethod, message, request)
      } else {
        val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
          Map("$push" -> Map("assignments" -> newRecord)))
        if (updateResult.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB error: $updateResult")
        val message = s"Added organization $organizationOid to (Activity $activityOid, role $theRole)"
        BWLogger.audit(getClass.getName, request.getMethod, message, request)
      }

    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}