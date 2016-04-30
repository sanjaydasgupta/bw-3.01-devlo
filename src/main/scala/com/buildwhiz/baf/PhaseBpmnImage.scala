package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.{BpmnUtils, HttpUtils}
import com.buildwhiz.infra.BWLogger

class PhaseBpmnImage extends HttpServlet with HttpUtils with BpmnUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val bpmnFileName = parameters("bpmn_name").replaceAll(" ", "-")
      val diagramStream = getProcessDiagram(bpmnFileName)
      val buffer = new Array[Byte](4096)
      val printStream = response.getOutputStream
      def copyDiagramToOutput(): Unit = {
        val len = diagramStream.read(buffer)
        if (len > 0) {
          printStream.write(buffer, 0, len)
          copyDiagramToOutput()
        }
      }
      copyDiagramToOutput()
      response.setContentType("image/png")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
