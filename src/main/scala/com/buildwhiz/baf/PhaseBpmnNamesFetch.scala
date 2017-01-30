package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWLogger
import com.buildwhiz.utils.BpmnUtils

class PhaseBpmnNamesFetch extends HttpServlet with BpmnUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val prefix = "1.01/Phase-"
      val phaseNames: Seq[String] = getDeployedResources.map(_.getName).filter(_.matches(s"$prefix.+\\.bpmn")).
        map(s => s""""${s.substring(prefix.length, s.length - 5)}"""").distinct
      writer.print(phaseNames.sorted.mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

}