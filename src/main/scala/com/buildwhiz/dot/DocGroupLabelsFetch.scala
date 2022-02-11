package com.buildwhiz.dot

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class DocGroupLabelsFetch extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val user: DynDoc = getUser(request)
      val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head

      val usersLabelRecords: Seq[DynDoc] = if (person.has("labels"))
        person.labels[Many[Document]]
      else
        Seq.empty[Document]

      val docOidSet = parameters("document_ids").split(",").map(id => new ObjectId(id.trim)).toSet

      val commonLabels = usersLabelRecords.
          filter(_.document_ids[Many[ObjectId]].toSet.intersect(docOidSet) == docOidSet).
          map(_.name[String]).mkString(",")

      response.getWriter.println(commonLabels)
      response.setContentType("text/plain")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"""EXIT-OK ($commonLabels)""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}
