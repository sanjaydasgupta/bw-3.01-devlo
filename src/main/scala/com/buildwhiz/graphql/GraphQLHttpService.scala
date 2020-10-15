package com.buildwhiz.graphql

import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import scala.collection.JavaConverters._

class GraphQLHttpService extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val query = parameters("query")
      val result = Sample.execute(query)
      val json = new Document(result.toSpecification).toJson
      response.getWriter.println(json)
      response.setContentType("application/json")
      if (result.getErrors.isEmpty) {
        BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK (length: ${json.length})", request)
      } else {
        val messages = result.getErrors.iterator.asScala.map(_.getMessage).mkString("; ")
        BWLogger.log(getClass.getName, "doGet()", s"EXIT-ERROR ($messages)", request)
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}