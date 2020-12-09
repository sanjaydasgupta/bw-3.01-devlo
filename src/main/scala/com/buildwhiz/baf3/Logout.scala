package com.buildwhiz.baf3

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class Logout extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request, isLogin = true)
    try {
      if (getSessionAlternatives(request).getAttribute("bw-user") != null) {
        val user: DynDoc = getUser(request)
        getSessionAlternatives(request).removeAttribute("bw-user")
        val userNameAndId = f"${user.first_name[String]}%s ${user.last_name[String]}%s (${user._id[ObjectId]}%s)"
        BWLogger.log(getClass.getName, request.getMethod, s"EXIT (Logout $userNameAndId)", request)
      } else {
        BWLogger.log(getClass.getName, request.getMethod, "EXIT (Logout unknown user)", request)
      }
      request.getSession.invalidate()
    } catch {
      case t: Throwable =>
        val parameters = getParameterMap(request)
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", parameters.toSeq: _*)
        //t.printStackTrace()
        throw t
    }
  }
}
