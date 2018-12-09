package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.utils.{BWLogger, BpmnUtils}

class PhaseBpmnNamesFetch extends HttpServlet with BpmnUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    try {
      val prefix = ".+/Phase-"
      val phaseNames: Seq[String] = getDeployedResources.map(_.getName).filter(_.matches(s"$prefix.+\\.bpmn")).
        map(s => {
          val idx = s.lastIndexOf("/Phase-")
          val idx2 = s.lastIndexOf(".bpmn")
          val pn = s.substring(idx + "/Phase-".length, idx2)
          s""" "$pn" """.trim
        }).distinct
      response.getWriter.print(phaseNames.sorted.mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK (${phaseNames.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}