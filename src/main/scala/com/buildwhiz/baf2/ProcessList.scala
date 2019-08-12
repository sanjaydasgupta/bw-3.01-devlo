package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import BWMongoDB3._
import DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId
import org.bson.Document

class ProcessList extends HttpServlet with HttpUtils with DateTimeUtils {
  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    def process2json(process: DynDoc, user: DynDoc): String = {
      val adminPersonOid = process.admin_person_id[ObjectId]
      val adminPerson: DynDoc = BWMongoDB3.persons.find(Map("_id" -> adminPersonOid)).head
      val adminName = PersonApi.fullName(adminPerson)
      val timestamps: DynDoc = process.timestamps[Document]
      val timeZone = user.tz[String]
      val (startTime, endTime) = if (timestamps.has("end")) {
        (dateTimeString(timestamps.start[Long], Some(timeZone)), dateTimeString(timestamps.end[Long], Some(timeZone)))
      } else if (timestamps.has("start")) {
        (dateTimeString(timestamps.start[Long], Some(timeZone)), "NA")
      } else {
        ("NA", "NA")
      }
      val canLaunch = adminPersonOid == user._id[ObjectId] && process.status[String] == "defined"
      val processDoc = new Document("_id", process._id[ObjectId]).append("name", process.name[String]).
          append("status", process.status[String]).append("display_status", ProcessApi.displayStatus(process)).
          append("start_time", startTime).append("end_time", endTime).
          append("admin_person_id", adminPersonOid.toString).append("bpmn_name", process.bpmn_name[String]).
          append("manager", adminName).append("can_launch", canLaunch)
      bson2json(processDoc)
    }

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
      val processes: Seq[DynDoc] = PhaseApi.allProcesses(phaseOid)
      val user: DynDoc = getUser(request)
      val detail = parameters.get("detail") match {
        case None => false
        case Some(dv) => dv.toBoolean
      }
      if (detail) {
        val parentProject = PhaseApi.parentProject(phaseOid)
        response.getWriter.print(processes.map(process =>
          bson2json(ProcessApi.processProcess(process, parentProject, user._id[ObjectId]).asDoc)).mkString("[", ", ", "]"))
      } else {
        response.getWriter.print(processes.map(process => process2json(process, user)).mkString("[", ", ", "]"))
      }
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${processes.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}