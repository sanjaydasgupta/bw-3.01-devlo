package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class OrganizationInfoSet extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {

      def rating2int(rating: String): Int = {
        if (rating.matches("[1-5]"))
          rating.toInt
        else
          throw new IllegalArgumentException(s"bad rating: '$rating'")
      }

      val parameterConverters: Map[String, String => Any] = Map(
        ("name", rawName => {val name = rawName.trim; OrganizationApi.validateNewName(name); name}),
        ("reference", _.trim),
        ("rating", rating2int),
        ("skills", _.split(",").map(_.trim).toSeq.filter(_.trim.nonEmpty).asJava),
        ("years_experience", _.toDouble),
        ("active", _.toBoolean),
        ("areas_of_operation", _.trim),
        ("organization_id", new ObjectId(_))
      )
      val parameterMap = getParameterMap(request)

      val unknownParameters = parameterMap.keySet.toArray.filterNot(parameterConverters.contains)
      if (unknownParameters.nonEmpty)
        throw new IllegalArgumentException(s"""Unknown parameter(s): ${unknownParameters.mkString(", ")}""")

      if (!parameterMap.contains("organization_id"))
        throw new IllegalArgumentException("organization_id not provided")

      val parameterValues = parameterConverters.map(pc => {
        val paramName = pc._1
        val paramConverter = pc._2
        val exists = parameterMap.contains(paramName)
        (paramName, paramConverter, exists)
      }).filter(_._3).map(t => (t._1, t._2(parameterMap(t._1))))

      val (parameterNamesAndValues, organizationIdAndValue) = parameterValues.partition(_._1 != "organization_id")

      val organizationOid = organizationIdAndValue.head._2.asInstanceOf[ObjectId]
      val user: DynDoc = getUser(request)
      val userIsAdmin = PersonApi.isBuildWhizAdmin(Right(user))
      val inSameOrganization = if (user.has("organization_id"))
        user.organization_id[ObjectId] == organizationOid
      else
        false
      if (!userIsAdmin && !inSameOrganization)
        throw new IllegalArgumentException("Not permitted")

      if (parameterNamesAndValues.isEmpty)
        throw new IllegalArgumentException("No parameters found")

      val updateResult = BWMongoDB3.organizations.updateOne(Map("_id" -> organizationOid),
          Map("$set" -> parameterNamesAndValues.toMap))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

      response.setStatus(HttpServletResponse.SC_OK)
      val parametersChanged = parameterNamesAndValues.map(_._1).mkString("[", ", ", "]")
      val message = s"""Updated parameters $parametersChanged of organization $organizationOid"""
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}