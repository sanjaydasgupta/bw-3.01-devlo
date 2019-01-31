package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import BWMongoDB3._
import DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId
import org.bson.Document

class ProcessList extends HttpServlet with HttpUtils {
  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    def process2json(process: DynDoc): String = {
      val adminPersonOid = process.admin_person_id[ObjectId]
      val adminPerson: DynDoc = BWMongoDB3.persons.find(Map("_id" -> adminPersonOid)).head
      val adminName = s"${adminPerson.first_name[String]} ${adminPerson.last_name[String]}"
      val processDoc = new Document("_id", process._id[ObjectId]).append("name", process.name[String]).
          append("status", process.status[String]).append("start_time", "0000-00-00 00:00").
          append("end_time", "0000-00-00 00:00").append("admin_person_id", adminPersonOid.toString).
          append("manager", adminName)
      bson2json(processDoc)
    }

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
      val processes: Seq[DynDoc] = PhaseApi.allProcesses(phaseOid)
      response.getWriter.print(processes.map(process2json).mkString("[", ", ", "]"))
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