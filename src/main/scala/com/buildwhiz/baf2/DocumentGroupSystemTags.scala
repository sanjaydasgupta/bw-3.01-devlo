package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class DocumentGroupSystemTags extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val systemTagCount = tagsFetch(request, response)
    BWLogger.log(getClass.getName, request.getMethod,
      s"EXIT-OK ($systemTagCount)", request)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val systemTagCount = tagsFetch(request, response)
    BWLogger.log(getClass.getName, request.getMethod,
      s"EXIT-OK ($systemTagCount)", request)
  }

  def tagsFetch(request: HttpServletRequest, response: HttpServletResponse): Int = {
    val parameters = getParameterMap(request)
    try {
      val docIds: Seq[String] = (if (request.getMethod == "GET") {
        parameters("document_ids")
      } else {
        getStreamData(request)
      }).split(",").map(_.trim)

      val docOids: Seq[ObjectId] = docIds.map(id => new ObjectId(id.trim))

      val documentRecords: Seq[DynDoc] = BWMongoDB3.document_master.find(Map("_id" -> Map("$in" -> docOids)))

      val systemTags: Seq[String] = documentRecords.
        flatMap(docRecord => DocumentApi.getSystemTags(docRecord)).distinct.sorted
      val systemTagsArray = systemTags.map(ul => s""""$ul"""").mkString("[", ", ", "]")

      response.getWriter.println(systemTagsArray)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      systemTagsArray.length
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}

