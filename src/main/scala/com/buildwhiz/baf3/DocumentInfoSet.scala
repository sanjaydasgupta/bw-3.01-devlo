package com.buildwhiz.baf3

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import org.bson.types.ObjectId

import scala.jdk.CollectionConverters._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class DocumentInfoSet extends HttpServlet with HttpUtils with MailUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val t0 = System.currentTimeMillis()
      val user: DynDoc = getPersona(request)
      def csv2array(csv: String): Many[String] = csv.split(",").map(_.trim).filter(_.nonEmpty).toSeq.asJava
      val parameterInfo: Map[String, (String=>Any, String)] = Map("name" -> (n => n, "name"),
        "document_id" -> (id => new ObjectId(id), "document_id"), "tags" -> (csv2array, "labels"),
        "comment" -> (n => n, "comment"))

      val unknownParameters = parameters.keySet.filterNot(_ == "JSESSIONID").
          filterNot(paramName => parameterInfo.contains(paramName))
      if (unknownParameters.nonEmpty)
        throw new IllegalArgumentException(s"""bad parameters found: ${unknownParameters.mkString(", ")}""")

      val convertedParameterValues: Map[String, Any] = parameterInfo.filter(kv => parameters.contains(kv._1)).
          map(kv => (kv._2._2, kv._2._1(parameters(kv._1))))
      val documentOid = convertedParameterValues("document_id").asInstanceOf[ObjectId]
      val parameterValues = convertedParameterValues.filterNot(_._1 == "document_id")
      if (parameterValues.isEmpty)
        throw new IllegalArgumentException("No parameter values found")
      val updater = if (parameterValues.contains("comment")) {
        val commentRecord = Map("text" -> parameterValues("comment"), "author_person_id" -> user._id[ObjectId],
            "timestamp" -> System.currentTimeMillis)
        if (parameterValues.size > 1) {
          Map($set -> parameterValues.filterNot(_._1 == "comment"), $push -> Map("comments" -> commentRecord))
        } else {
          Map($push -> Map("comments" -> commentRecord))
        }
      } else {
        Map($set -> parameterValues)
      }
      val updateResult = BWMongoDB3.document_master.updateOne(Map("_id" -> documentOid), updater)
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      val values = parameterValues.map(kv => "%s=%s".format(kv._1, kv._2)).mkString(", ")
      val delay = System.currentTimeMillis() - t0
      val message = s"""time: $delay ms, Set parameters $values for document $documentOid"""
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
      response.getWriter.print(successJson())
      response.setContentType("application/json")
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

