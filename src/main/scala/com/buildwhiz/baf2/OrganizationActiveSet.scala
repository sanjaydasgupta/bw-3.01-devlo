package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class OrganizationActiveSet extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      // Disabled temporarily for testing ...
//      val user: DynDoc = getUser(request)
//      val userOid = user._id[ObjectId]
//      if (!PersonApi.isBuildWhizAdmin(userOid)) {
//        throw new IllegalArgumentException("Not permitted")
//      }

      val organizationOid = new ObjectId(parameters("organization_id"))
      if (!OrganizationApi.exists(organizationOid))
        throw new IllegalArgumentException(s"Bad organization_id '$organizationOid'")

      val activeString = parameters("active")

      val activeValue = if (activeString.matches("true|false"))
        activeString.toBoolean
      else
        throw new IllegalArgumentException(s"Bad active value: '$activeString'")

      val updateResult = BWMongoDB3.organizations.updateOne(Map("_id" -> organizationOid),
        Map("$set" -> Map("active" -> activeValue)))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

      val message = s"Set organization active to '$activeValue'"
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}