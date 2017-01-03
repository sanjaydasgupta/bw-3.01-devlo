package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.HttpUtils
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.Document

import scala.collection.JavaConverters._

class DocumentRecordFind extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val properties = Seq("category", "subcategory", "content", "name", "description")
      val query = (("project_id" -> project430ForestOid) +:
          properties.map(p => (p, parameters(p))).filter(kv => kv._2.nonEmpty && kv._2 != "Any")).map {
            case ("name", value) => ("name", Map("$regex" -> s".*$value.*", "$options" -> "i"))
            case ("description", value) => ("description", Map("$regex" -> s".*$value.*", "$options" -> "i"))
            case p => p
          }.toMap
      val records: Seq[Document] = BWMongoDB3.document_master.find(query).asScala.toSeq
      val jsons = records.map(_.toJson).mkString("[", ", ", "]")
      response.getOutputStream.println(jsons)
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
