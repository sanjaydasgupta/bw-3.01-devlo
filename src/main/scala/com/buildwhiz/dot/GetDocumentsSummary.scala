package com.buildwhiz.dot

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId
import org.bson.Document

class GetDocumentsSummary extends HttpServlet with HttpUtils {

  private def getDocuments: Seq[Document] = {
    val docs: Seq[Document] = Seq(
      Map("_id" -> "a123456789012", "name" -> "Foundation Drawings", "phase" -> "School Foundation",
        "labels" -> Map("system" -> Seq("architecture", "design"), "user" -> Seq("environment", "water")),
        "author" -> "Winston Chang", "type" -> "pdf", "date" -> "2017-11-12 13:15 PT",
        "downloadUrl" -> ""),
      Map("_id" -> "b123456789012", "name" -> "Roofing Special Materials", "phase" -> "School Roof",
        "labels" -> Map("system" -> Seq("construction", "materials"), "user" -> Seq("plastics", "environment", "rain")),
        "author" -> "Kelly Heath", "type" -> "pdf", "date" -> "2017-11-22 10:31 PT",
        "downloadUrl" -> "")
    )
    docs
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val personOid = getUser(request).y._id[ObjectId]

      //val actionOrder = Map("prerequisite" -> 1, "main" -> 2, "review" -> 3)
      val allDocuments = getDocuments()
      writer.print(allDocuments.map(document => bson2json(document)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}