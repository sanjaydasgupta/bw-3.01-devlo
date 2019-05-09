package com.buildwhiz.baf2

import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class RoleListSecondary extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val activityOid = new ObjectId(parameters("activity_id"))
      val theActivity = ActivityApi.activityById(activityOid)
//      val user: DynDoc = getUser(request)
//      if (!ProcessApi.canManage(user._id[ObjectId], theActivity))
//        throw new IllegalArgumentException("Not permitted")
      response.getWriter.print(RoleListSecondary.secondaryRoles.mkString("[\"", "\", \"", "\"]"))
      response.setContentType("application/json")
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

object RoleListSecondary {
  val secondaryRoles: Seq[String] = Seq("Pre-Approval", "Post-Approval", "CC", "Other")
}