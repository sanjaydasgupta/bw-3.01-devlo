package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

class DocLabelsFetch extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      val labels: Seq[String] = if (person.has("labels")) person.labels[Many[String]] else Seq.empty[String]
      val sortedLabels: Seq[String] = labels.sorted
      response.getWriter.println(sortedLabels.map(label => s""""$label"""").mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", s"EXIT-OK (${labels.length} labels)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}
