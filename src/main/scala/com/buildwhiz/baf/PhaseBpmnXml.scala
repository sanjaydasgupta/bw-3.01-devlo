package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWLogger
import com.buildwhiz.utils.{BpmnUtils, HttpUtils}

import scala.collection.mutable

class PhaseBpmnXml extends HttpServlet with HttpUtils with BpmnUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val bpmnFileName = parameters("bpmn_name").replaceAll(" ", "-")
      val processDefinition = getProcessDefinition(bpmnFileName)
      val processModelStream = getProcessModel(bpmnFileName)
      val blockBuffer = new Array[Byte](4096)
      val byteBuffer = mutable.Buffer.empty[Byte]
      //val printStream = response.getOutputStream
      def copyModelToOutput(): Unit = {
        val len = processModelStream.read(blockBuffer)
        if (len > 0) {
          byteBuffer.append(blockBuffer.take(len): _*)
          //printStream.write(blockBuffer, 0, len)
          copyModelToOutput()
        }
      }
      copyModelToOutput()
      val xml = new String(byteBuffer.toArray).replaceAll("\n", "\\\\n").replaceAll("\r", "\\\\r").
          replaceAll("\t", "\\\\t").replaceAll("\"", "\\\\\"").replaceAll("\'", "\\\\\'")
      val json = s"""{"id": "${processDefinition.getId}", "bpmn20Xml": "$xml"}"""
      response.getWriter.println(json)
      response.setContentType("application/json")
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
