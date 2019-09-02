package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class PhaseInfoSet extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      def nop(input: String): Any = input
      def managers2roles(mids: String): Many[Document] = {
        val phaseManagerOids: Seq[ObjectId] = mids.split(",").map(_.trim).filter(_.nonEmpty).
            distinct.map(new ObjectId(_))

        val badManagerIds = phaseManagerOids.filterNot(PersonApi.exists)
        if (badManagerIds.nonEmpty)
          throw new IllegalArgumentException(s"""Bad project_manager_ids: ${badManagerIds.mkString(", ")}""")

        val managersInRoles = phaseManagerOids.map(oid =>
          new Document("role_name", "Project-Manager").append("person_id", oid)
        ).asJava

        managersInRoles
      }
      val parameterConverters: Map[String, (String => Any, String)] = Map(
        ("name", (nop, "name")), ("description", (nop, "description")),
        ("phase_id", (nop, "phase_id")), ("manager_ids", (managers2roles, "assigned_roles"))
      )
      val parameterNames = parameterConverters.keys.toSeq
      val postData = Document.parse(getStreamData(request))
      val unknownParameters = postData.keySet.toArray.filterNot(parameterNames.contains)
      if (unknownParameters.nonEmpty)
        throw new IllegalArgumentException(s"""Unknown parameter(s): ${unknownParameters.mkString(", ")}""")
      if (!postData.containsKey("phase_id"))
        throw new IllegalArgumentException("phase_id not provided")
      val phaseOid = new ObjectId(postData.remove("phase_id").asInstanceOf[String])
      val mongoDbNameValuePairs = parameterNames.filter(postData.containsKey).
          map(paramName => (parameterConverters(paramName)._2,
          parameterConverters(paramName)._1(postData.getString(paramName))))
      if (mongoDbNameValuePairs.isEmpty)
        throw new IllegalArgumentException("No parameters found")
      val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
          Map("$set" -> mongoDbNameValuePairs.toMap))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      response.setStatus(HttpServletResponse.SC_OK)
      val parametersChanged = mongoDbNameValuePairs.map(_._1).mkString("[", ", ", "]")
      val message = s"""Updated parameters $parametersChanged of phase $phaseOid"""
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}