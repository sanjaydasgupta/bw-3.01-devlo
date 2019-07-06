package com.buildwhiz.tools

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.utils.{BWLogger, HttpUtils}

class MainProgramLauncher extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    doPost(request, response)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val parameters = getParameterMap(request)
      val args: Array[String] = if (parameters.contains("args")) parameters("args").split("\\s+") else Array.empty
      val clazz = Class.forName(s"""com.buildwhiz.infra.scripts.${parameters("program")}""")
      val mainMethod = clazz.getMethods.filter(_.getName == "main").map(m => (m, m.getParameterTypes.length)).head
      try {
        if (mainMethod._2 == 1)
          clazz.getMethod("main", classOf[Array[String]]).invoke(null, args)
        else if (mainMethod._2 == 2)
          clazz.getMethod("main", classOf[HttpServletRequest], classOf[Array[String]]).
            invoke(null, request, args)
        BWLogger.log(getClass.getName, "doPost/Get", s"EXIT-OK", request)
      } catch {
        case t: Throwable =>
          BWLogger.log(getClass.getName, "doPost/Get", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
          t.printStackTrace()
          throw t
      }
    }
  }
}
