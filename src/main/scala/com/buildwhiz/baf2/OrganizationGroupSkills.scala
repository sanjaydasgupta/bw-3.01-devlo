package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class OrganizationGroupSkills extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val skillCount = skillsFetch(request, response)
    BWLogger.log(getClass.getName, request.getMethod,
      s"EXIT-OK ($skillCount)", request)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val skillCount = skillsFetch(request, response)
    BWLogger.log(getClass.getName, request.getMethod,
      s"EXIT-OK ($skillCount)", request)
  }

  def skillsFetch(request: HttpServletRequest, response: HttpServletResponse): Int = {
    val parameters = getParameterMap(request)
    try {
      val orgIds: Seq[String] = (if (request.getMethod == "GET") {
        parameters("organization_ids")
      } else {
        getStreamData(request)
      }).split(",").map(_.trim).filter(_.nonEmpty).toSeq

      val organizationOids: Seq[ObjectId] = orgIds.map(id => new ObjectId(id.trim))

      val organizationRecords: Seq[DynDoc] = BWMongoDB3.organizations.find(Map("_id" -> Map("$in" -> organizationOids)))

      val skills: Seq[String] = organizationRecords.flatMap(_.skills[Many[String]]).distinct.sorted

      val skillsArray = skills.map(skill => s""""$skill"""").mkString("[", ", ", "]")

      response.getWriter.println(skillsArray)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      skills.length
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}

