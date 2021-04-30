package com.buildwhiz.baf3

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class DocumentInfoSet extends HttpServlet with HttpUtils with MailUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {

      def csv2array(csv: String): Many[String] = csv.split(",").map(_.trim).filter(_.nonEmpty).toSeq.asJava
      val parameterInfo: Map[String, (String=>Any, String)] = Map("name" -> (n => n, "name"),
        "document_id" -> (id => new ObjectId(id), "document_id"), "tags" -> (csv2array, "labels"))

      val unknownParameters = parameters.keySet.filterNot(_ == "JSESSIONID").
          filterNot(paramName => parameterInfo.contains(paramName))
      if (unknownParameters.nonEmpty)
        throw new IllegalArgumentException(s"""bad parameters found: ${unknownParameters.mkString(", ")}""")

      val parameterValues: Map[String, Any] = parameterInfo.filter(kv => parameters.contains(kv._1)).
          map(kv => (kv._2._2, kv._2._1(parameters(kv._1))))
      val documentOid = parameterValues("document_id").asInstanceOf[ObjectId]
      val setter: Map[String, Any] = parameterValues.filterNot(_._1 == "ObjectId")

      val updateResult = BWMongoDB3.document_master.updateOne(Map("_id" -> documentOid), Map("$set" -> setter))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      val values = setter.map(kv => "%s=%s".format(kv._1, kv._2)).mkString(", ")
      val message = s"""Set parameters $values for document $documentOid"""
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

