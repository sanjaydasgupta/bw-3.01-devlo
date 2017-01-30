package com.buildwhiz.obsolete

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import com.buildwhiz.utils.HttpUtils
import org.bson.Document

import scala.collection.JavaConverters._

class DocumentRecordCreate extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val properties = Seq("category", "subcategory", "content", "name", "description")
      val query = (("project_id" -> project430ForestOid) +:
        properties.map(p => (p, parameters(p))).filter(kv => kv._2.nonEmpty && kv._2 != "Any")).toMap
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
