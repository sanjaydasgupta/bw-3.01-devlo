package com.buildwhiz.tools

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.HttpUtils
import com.buildwhiz.infra.BWLogger

class MainProgramLauncher extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    doPost(request, response)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val parameters = getParameterMap(request)
      val clazz = Class.forName(s"""com.buildwhiz.infra.${parameters("program")}""")
      clazz.getMethod("main", classOf[Array[String]]).invoke(null, Array.empty[String])
      BWLogger.log(getClass.getName, "doPost/Get", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost/Get", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
