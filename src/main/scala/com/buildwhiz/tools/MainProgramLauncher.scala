package com.buildwhiz.tools

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWLogger
import com.buildwhiz.utils.HttpUtils

class MainProgramLauncher extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    doPost(request, response)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val parameters = getParameterMap(request)
      val args: Array[String] = if (parameters.contains("args")) parameters("args").split("\\s+") else Array.empty
      val clazz = Class.forName(s"""com.buildwhiz.infra.${parameters("program")}""")
      try {
        clazz.getMethod("main", classOf[Array[String]]).invoke(null, args)
      } catch {
        case t: Throwable =>
          val inst = clazz.newInstance()
          clazz.getMethod("doGet", classOf[HttpServletRequest], classOf[HttpServletResponse]).invoke(inst , request, response)
      }
      BWLogger.log(getClass.getName, "doPost/Get", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost/Get", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
