package com.buildwhiz.baf2

import com.buildwhiz.dot.GetDocumentsSummary
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class DocumentGroupSystemLabels extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val systemLabelCount = labelsFetch(request, response)
    BWLogger.log(getClass.getName, request.getMethod,
      s"EXIT-OK ($systemLabelCount)", request)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val systemLabelCount = labelsFetch(request, response)
    BWLogger.log(getClass.getName, request.getMethod,
      s"EXIT-OK ($systemLabelCount)", request)
  }

  def labelsFetch(request: HttpServletRequest, response: HttpServletResponse): Int = {
    val parameters = getParameterMap(request)
    try {
      val docIds: Seq[String] = if (request.getMethod == "GET") {
        parameters("document_ids").split(",")
      } else {
        val postData: DynDoc = Document.parse(getStreamData(request))
        val ids: Seq[String] = postData.document_ids[Many[String]]
        ids
      }

      val docOids: Seq[ObjectId] = docIds.map(id => new ObjectId(id.trim))

      val documentRecords: Seq[DynDoc] = BWMongoDB3.document_master.find(Map("_id" -> Map("$in" -> docOids)))

      val systemLabels: Seq[String] = documentRecords.
        flatMap(docRecord => GetDocumentsSummary.getSystemLabels(docRecord)).distinct.sorted
      val csvSystemLabels = systemLabels.map(ul => s""""$ul"""").mkString("[", ", ", "]")

      response.getWriter.println(csvSystemLabels)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      csvSystemLabels.length
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}

