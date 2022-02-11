package com.buildwhiz.etc

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class LogoutPost extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request, isLogin = true)
    try {
      if (request.getSession.getAttribute("bw-user") != null) {
        val user: DynDoc = getUser(request)
        request.getSession.removeAttribute("bw-user")
        request.getSession.invalidate()
        val userNameAndId = f"${user.first_name[String]}%s ${user.last_name[String]}%s (${user._id[ObjectId]}%s)"
        BWLogger.log(getClass.getName, request.getMethod, s"Logout ($userNameAndId)", request)
      }
    } catch {
      case t: Throwable =>
        val parameters = getParameterMap(request)
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", parameters.toSeq: _*)
        //t.printStackTrace()
        throw t
    }
  }
}
