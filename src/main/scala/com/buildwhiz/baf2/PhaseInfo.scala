package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class PhaseInfo extends HttpServlet with HttpUtils {

  private def isEditable(phase: DynDoc, user: DynDoc): Boolean = {
    val userOid = user._id[ObjectId]
    phase.admin_person_id[ObjectId] == userOid || phase.assigned_roles[Many[Document]].exists(role =>
      role.person_id[ObjectId] == userOid && role.role_name[String].matches("(Project|Phase)-Manager"))
  }

  private def processInformation(phase: DynDoc): Many[Document] = {
    val processes: Seq[DynDoc] = PhaseApi.allProcesses(phase._id[ObjectId])
    val returnValue: Seq[Document] = processes.map(process => {
      Map("name" -> process.name[String], "status" -> process.status[String],
          "start_date" -> "???", "end_date" -> "???")
    })
    returnValue.asJava
  }

  private def phase2json(phase: DynDoc, editable: Boolean): String = {
    val description = new Document("editable", editable).append("value", phase.description[String])
    val status = new Document("editable", false).append("value", phase.status[String])
    val name = new Document("editable", editable).append("value", phase.name[String])
    val projectDoc = new Document("name", name).append("description", description).
        append("status", status).append("process_info", processInformation(phase))
    projectDoc.toJson
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
      val phaseRecord: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val user: DynDoc = getUser(request)
      val phaseIsEditable = isEditable(phaseRecord, user)
      response.getWriter.print(phase2json(phaseRecord, phaseIsEditable))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}