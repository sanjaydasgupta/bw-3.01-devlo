package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId
import org.bson.Document

class DocumentLabelsFetch extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      val labels: Seq[DynDoc] = if (person.has("labels")) person.labels[Many[Document]] else Seq.empty[Document]
      val sortedLabels: Seq[String] = labels.map(_.name[String]).sorted
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
