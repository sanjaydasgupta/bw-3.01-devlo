package com.buildwhiz.baf3

import com.buildwhiz.baf2.{PersonApi, OrganizationApi}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.jdk.CollectionConverters._

class PartnerInfoSet extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val user = getPersona(request)
      if (!PersonApi.isBuildWhizAdmin(Right(user)))
        throw new IllegalArgumentException("Not permitted")
      val parameterString = getStreamData(request)
      BWLogger.log(getClass.getName, request.getMethod, s"Parameter-String: $parameterString", request)
      val postData = Document.parse(parameterString)
      if (!postData.containsKey("organization_id"))
        throw new IllegalArgumentException("organization_id not provided")
      val organizationId = postData.remove("organization_id").asInstanceOf[String]
      val nameValuePairs = postData.entrySet.asScala.map(es => (es.getKey, es.getValue)).toSeq
      val message = PartnerInfoSet.setPartnerFields(organizationId, nameValuePairs)
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
      response.getWriter.print(successJson())
      response.setContentType("application/json")
    } catch {
      case t: Throwable =>
        reportFatalException(t, getClass.getName, request, response)
    }
  }

}

object PartnerInfoSet extends DateTimeUtils {

  private def string2Boolean(s: String) = s.toBoolean
  private def csv2array(csv: String): Many[String] = csv.split(",").map(_.trim).toSeq.asJava
  private def string2Int(s: String) = s.toInt
  private def identity[T](a: T) = a

  private val parameterNames: Map[String, String => Any] = Map(
    ("project_sponsor", string2Boolean), ("design_partner", string2Boolean), ("trade_partner", string2Boolean),
    ("serving_area", identity), ("profile", identity), ("past_projects", identity),
    ("reviews", identity), ("preferences", identity), ("name", identity), ("rating", string2Int),
    ("active", string2Boolean), ("skills", csv2array)
  )

  private val dataFieldNames = Map("serving_area" -> "areas_of_operation")

  def setPartnerFields(organizationId: String, nameValuePairs: Seq[(String, AnyRef)]): String = {
    if (nameValuePairs.isEmpty)
      throw new IllegalArgumentException("No parameters found")
    val unknownParameters = nameValuePairs.map(_._1).filterNot(parameterNames.containsKey)
    if (unknownParameters.nonEmpty)
      throw new IllegalArgumentException(s"""Unknown parameter(s): ${unknownParameters.mkString(", ")}""")
    nameValuePairs.find(_._1 == "name") match {
      case Some((_, name)) =>
        OrganizationApi.validateNewName(name.toString)
      case None =>
    }
    val dbSetters: Map[String, Any] = nameValuePairs.map(
        pair => (dataFieldNames.getOrElse(pair._1, pair._1), parameterNames(pair._1)(pair._2.toString))).toMap
    val organizationOid = new ObjectId(organizationId)
    val updateResult = BWMongoDB3.organizations.updateOne(Map("_id" -> organizationOid), Map("$set" -> dbSetters))
    if (updateResult.getMatchedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

    val parametersChanged = nameValuePairs.map(_._1).mkString("[", ", ", "]")
    val message = s"""Updated parameters $parametersChanged of partner $organizationOid"""
    message
  }

}