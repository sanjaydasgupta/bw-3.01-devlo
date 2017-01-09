package com.buildwhiz.etc

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWLogger
import com.buildwhiz.{CryptoUtils, HttpUtils}

import scala.sys.process._

class SystemMonitor extends HttpServlet with HttpUtils with CryptoUtils {

  private def dfh(response: HttpServletResponse): Unit = {
    val output: String = "df -h".!!
    val lines = output.split("\n").map(_.trim).filter(_.nonEmpty)
    val fieldedLines = lines.map(_.split("\\s+"))
    val fieldedLines2 = fieldedLines.head.init +: fieldedLines.tail
    val json = fieldedLines2.map(_.mkString("[\"", "\", \"", "\"]")).mkString("[", ", ", "]")
    response.getWriter.print(json)
    response.setContentType("application/json")
  }

  private def topbn(response: HttpServletResponse): Unit = {
    val output: String = "top -b -n 1".!!
    val lines = output.split("\n").map(_.trim).filter(_.nonEmpty).dropWhile(line => !line.startsWith("PID"))
    val fieldedLines = lines.map(_.split("\\s+"))
    val json = fieldedLines.map(_.mkString("[\"", "\", \"", "\"]")).mkString("[", ", ", "]")
    response.getWriter.print(json)
    response.setContentType("application/json")
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      parameters("command") match {
        case "dfh" => dfh(response)
        case "topbn" => topbn(response)
        case _ =>
      }
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
