package com.buildwhiz.dot

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document

class GetDocumentsSummary extends HttpServlet with HttpUtils {

  private def getDocuments(user: DynDoc): Seq[Document] = {
    val count = user.first_name[String] match {
      case "Tester2" => 5
      case "Tester3" => 50
      case _ => 1
    }
    val docs: Seq[Document] = Seq(
      Map("_id" -> "a123456789012", "name" -> "Foundation Drawings", "phase" -> "School Foundation",
        "labels" -> Map("system" -> Seq("architecture", "design"), "user" -> Seq("environment", "water")),
        "author" -> "Winston Chang", "type" -> "pdf", "date" -> "2017-11-12 13:15 PT"),
      Map("_id" -> "b123456789012", "name" -> "Front Garden Landscape", "phase" -> "School Landscaping",
        "labels" -> Map("system" -> Seq("design"), "user" -> Seq("landscape", "garden")),
        "author" -> "Bhoomi Chugh", "type" -> "pdf", "date" -> "2017-07-09 09:10 PT"),
      Map("_id" -> "c123456789012", "name" -> "Roofing Special Materials", "phase" -> "School Roofing",
        "labels" -> Map("system" -> Seq("construction", "materials"), "user" -> Seq("plastics", "environment", "rain")),
        "author" -> "Kelly Heath", "type" -> "pdf", "date" -> "2017-11-22 10:31 PT")
    )
    (1 to count).map(_ => docs).reduce((a, b) => a ++ b)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    //val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val user: DynDoc = getUser(request)

      val allDocuments = getDocuments(user)
      writer.print(allDocuments.map(document => bson2json(document)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK (${allDocuments.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}