package com.buildwhiz.baf

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

import com.buildwhiz.dot.GetDocumentsSummary

class DocumentGroupFilterLabelsFetch extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val user: DynDoc = getUser(request)
      val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head

      val docOids = parameters("document_ids").split(",").map(id => new ObjectId(id.trim))

      val docOid2UserLabels: Map[ObjectId, Seq[String]] = GetDocumentsSummary.docOid2UserLabels(person)

      val userLabels: Seq[String] = docOids.
          flatMap(oid => if (docOid2UserLabels.contains(oid)) docOid2UserLabels(oid) else Seq.empty[String]).distinct
      val csvUserLabels = userLabels.map(ul => s"""{"name": "$ul"}""").mkString("[", ", ", "]")

      val documentRecords: Seq[DynDoc] = docOids.
          map(oid => BWMongoDB3.document_master.find(Map("_id" -> oid)).head)

      val systemLabels: Seq[String] = documentRecords.
          flatMap(docRecord => GetDocumentsSummary.getSystemLabels(docRecord)).distinct
      val csvSystemLabels = systemLabels.map(ul => s"""{"name": "$ul"}""").mkString("[", ", ", "]")

      response.getWriter.println(s"""{"user": $csvUserLabels, "system": $csvSystemLabels}""")
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod,s"""EXIT-OK""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}
