package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.HttpUtils
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.Document

import scala.collection.JavaConverters._

class DocumentRecordCreate extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val category = parameters("category")
      val subCategory = parameters("subcategory")
      val content = parameters("content")
      val name = parameters("name")
      val query = Map("project_id" -> project430ForestOid, "category" -> category, "subcategory" -> subCategory,
          "content" -> content, "name" -> name)
      if (BWMongoDB3.document_master.find(query).asScala.nonEmpty)
        throw new Throwable("Record already exists")
      BWMongoDB3.document_master.insertOne(query ++ Map("timestamp" -> System.currentTimeMillis,
        "versions" -> Seq.empty[Document]))
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
