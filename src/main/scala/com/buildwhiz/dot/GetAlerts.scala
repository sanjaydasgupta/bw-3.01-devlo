package com.buildwhiz.dot

import com.buildwhiz.api.RestUtils
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.BWLogger
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document

class GetAlerts extends HttpServlet with RestUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doGet", s"ENTRY", request)

    val user: DynDoc = getUser(request)
    val result: Document = user.first_name[String] match {
      case "Tester2" => Map("tasks" -> "5", "docs" -> "10", "rfis" -> "5")
      case "Tester3" => Map("tasks" -> "1", "docs" -> "5", "rfis" -> "0")
      case _ => Map("tasks" -> "2", "docs" -> "0", "rfis" -> "3")
    }
    val json = result.toJson
    response.getWriter.println(json)
    response.setContentType("application/json")
    response.setStatus(HttpServletResponse.SC_OK)

    BWLogger.log(getClass.getName, "doGet", s"EXIT-OK ($json)", request)
  }

}