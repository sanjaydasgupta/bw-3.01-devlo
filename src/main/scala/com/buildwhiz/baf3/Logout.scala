package com.buildwhiz.baf3

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId
import scala.sys.process._
import java.io.File

class Logout extends HttpServlet with HttpUtils {

  private def loadNewUi(request: HttpServletRequest): Unit = {
    if (new File("/home/ubuntu/vv.zip").exists) {
      BWLogger.log(getClass.getName, "loadNewUi", "Found 'vv.zip'", request)
      if ("rm -rf software/camunda-bpm-tomcat-7.8.0/server/apache-tomcat-8.0.47/webapps/vv".! == 0) {
        if ("unzip -q /home/ubuntu/vv.zip".! == 0) {
          if ("mv vv /home/ubuntu/software/camunda-bpm-tomcat-7.8.0/server/apache-tomcat-8.0.47/webapps".! == 0) {
//            if ("rm -f /home/ubuntu/vv.zip".! != 0) {
//              BWLogger.log(getClass.getName, "loadNewUi", "WARN: Failed to remove 'vv.zip'", request)
//            }
            BWLogger.log(getClass.getName, "loadNewUi", "EXIT-OK", request)
          } else {
            BWLogger.log(getClass.getName, "loadNewUi", "ERROR: 'unzip -q ...'", request)
          }
        } else {
          BWLogger.log(getClass.getName, "loadNewUi", "ERROR: 'mv buildwhiz ...'", request)
        }
      } else {
        BWLogger.log(getClass.getName, "loadNewUi", "ERROR: 'rm -rf ...'", request)
      }
    } else {
      BWLogger.log(getClass.getName, "loadNewUi", "No 'vv.zip' found", request)
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request, isLogin = true)
//    try {
//      loadNewUi(request)
//    } catch {
//      case t: Throwable =>
//        val parameters = getParameterMap(request)
//        BWLogger.log(getClass.getName, request.getMethod,
//          s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", parameters.toSeq: _*)
//    }
    try {
      if (getSessionAlternatives(request).getAttribute("bw-user") != null) {
        val user: DynDoc = getUser(request)
        getSessionAlternatives(request).removeAttribute("bw-user")
        val userNameAndId = f"${user.first_name[String]}%s ${user.last_name[String]}%s (${user._id[ObjectId]}%s)"
        BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (Logout $userNameAndId)", request)
      } else {
        BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK (Logout unknown user)", request)
      }
      request.getSession.invalidate()
    } catch {
      case t: Throwable =>
        val parameters = getParameterMap(request)
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", parameters.toSeq: _*)
        //t.printStackTrace()
        throw t
    }
  }
}
