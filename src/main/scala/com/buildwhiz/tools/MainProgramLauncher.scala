package com.buildwhiz.tools

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.utils.{BWLogger, HttpUtils}

class MainProgramLauncher extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    doPost(request, response)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val parameters = getParameterMap(request)
      val args: Array[String] = if (parameters.contains("args")) parameters("args").split("\\s+") else Array.empty
      val clazz = Class.forName(s"""com.buildwhiz.tools.scripts.${parameters("program")}""")
      val mainMethod = clazz.getMethods.filter(_.getName == "main").map(m => (m, m.getParameterTypes.length)).head
      if (mainMethod._2 == 1)
        clazz.getMethod("main", classOf[Array[String]]).invoke(null, args)
      else if (mainMethod._2 == 2)
        clazz.getMethod("main", classOf[HttpServletRequest], classOf[Array[String]]).
            invoke(null, request, args)
      else if (mainMethod._2 == 3)
        clazz.getMethod("main", classOf[HttpServletRequest], classOf[HttpServletResponse], classOf[Array[String]]).
          invoke(null, request, response, args)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod,
            s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
