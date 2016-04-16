package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{BWLogger, BWMongoDB3, Utils}
import BWMongoDB3._

import scala.collection.JavaConversions._

class MongoDBView extends HttpServlet with Utils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    val writer = response.getWriter
    try {
      parameters.get("collection_name") match {
        case None =>
          val names: Seq[String] = BWMongoDB3.collectionNames
          val counts: Seq[Long] = names.map(BWMongoDB3(_).count())
          val nameAndCounts = names.zip(counts).sortWith(_._1 < _._1)
          val jsonStrings = nameAndCounts.map(nc => s"""{"name": "${nc._1}", "count": ${nc._2}}""")
          writer.print(jsonStrings.mkString("[", ", ", "]"))
        case Some(collectionName) =>
          val docs: Seq[DynDoc] = BWMongoDB3(collectionName).find().limit(100).toSeq
          val jsonStrings: Seq[String] = docs.map(d => bson2json(d.asDoc))
          writer.print(jsonStrings.mkString("[", ", ", "]"))
      }
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
