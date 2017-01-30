package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import com.buildwhiz.utils.HttpUtils

import scala.collection.JavaConverters._

class DocumentSubcategoriesFetch extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val category = parameters("category")
      val records: Seq[DynDoc] = BWMongoDB3.document_master.find(Map("category" -> category)).asScala.toSeq
      val subcategories: Seq[String] = records.filter(_.has("subcategory")).map(_.subcategory[String]).
          distinct.sortBy(_.toLowerCase)
      val jsonArrayString = subcategories.map(sc => s""" "$sc" """.trim).mkString("[", ", ", "]")
      response.getOutputStream.println(jsonArrayString)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doPost", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
