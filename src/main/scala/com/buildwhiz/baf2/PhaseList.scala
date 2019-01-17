package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class PhaseList extends HttpServlet with HttpUtils {
  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    def phase2json(phase: DynDoc) = s"""{"_id": "${phase._id[ObjectId]}", "name": "${phase.name[String]}"}"""

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val user: DynDoc = getUser(request)
      val personOid = user._id[ObjectId]
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
      val isAdmin = freshUserRecord.roles[Many[String]].contains("BW-Admin")
      val parentProject: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val phases: Seq[DynDoc] = if (isAdmin) {
        ProjectApi.allPhases(parentProject)
      } else {
        ProjectApi.phasesByUser(personOid, parentProject)
      }
      response.getWriter.print(phases.map(phase2json).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${phases.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}