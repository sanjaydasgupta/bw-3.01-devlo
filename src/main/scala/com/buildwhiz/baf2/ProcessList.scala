package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class ProcessList extends HttpServlet with HttpUtils {
  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    def process2json(process: DynDoc) = s"""{"_id": "${process._id[ObjectId]}", "name": "${process.name[String]}"}"""

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
//      val user: DynDoc = getUser(request)
//      val personOid = user._id[ObjectId]
//      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
//      val isAdmin = freshUserRecord.roles[Many[String]].contains("BW-Admin")
//      val parentPhase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val phases: Seq[DynDoc] = Seq.empty[DynDoc] //if (isAdmin) {
//        ProjectApi.allPhases(parentPhase)
//      } else {
//        ProjectApi.phasesByUser(personOid, parentPhase)
//      }
      response.getWriter.print(phases.map(process2json).mkString("[", ", ", "]"))
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