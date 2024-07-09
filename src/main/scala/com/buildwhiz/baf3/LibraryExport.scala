package com.buildwhiz.baf3

import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.mutable
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class LibraryExport extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val parameters = getParameterMap(request)
      val phaseOid = new ObjectId(parameters("phase_id"))
      val description = parameters("description")
      val includeTaskDurations = parameters("task_durations").toBoolean
      val includeActivities = parameters("activities").toBoolean
      val includeActivityDurations = parameters("activity_durations").toBoolean
      val includeTeams = parameters("teams").toBoolean
      val includePartners = parameters("team_partners").toBoolean
      val includeMembers = parameters("team_members").toBoolean
      val outputBuffer = new mutable.ArrayBuffer[String]()
      response.setContentType("application/json")
      try {
        LibraryOperations.exportPhase(phaseOid, msg => outputBuffer.append(msg), description, request)
        response.getWriter.print(successJson(fields = Map("html" -> outputBuffer.mkString("\n"))))
        BWLogger.audit(getClass.getName, request.getMethod, s"Phase '$phaseOid' exported successfully", request)
      } catch {
        case t: Throwable =>
          val returnJson = new Document("ok", 0).append("message", "See details in log")
          response.getWriter.print(returnJson)
          val messages = t.getStackTrace.map(_.toString).filter(_.contains("at com.buildwhiz.")).mkString("\n")
          BWLogger.log(getClass.getName, request.getMethod, s"EXIT-ERROR: $messages", request)
      }
    } catch {
      case t: Throwable =>
        t.getStackTrace
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}