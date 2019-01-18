package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class ProjectInfo extends HttpServlet with HttpUtils {

  private def isEditable(project: DynDoc, user: DynDoc): Boolean = {
    val userOid = user._id[ObjectId]
    project.admin_person_id[ObjectId] == userOid || project.assigned_roles[Many[Document]].
        exists(role => role.person_id[ObjectId] == userOid && role.role_name[String] == "Project-Manager")
  }

  private def project2json(project: DynDoc, editable: Boolean): String = {
    val bareGpsLocation: Document = if (project.has("gps_location"))
      project.gps_location[Document]
    else
      Document.parse("""{"latitude": 0.0, "longitude": 0.0}""")
    val gpsLocation = new Document("editable", editable).append("value", bareGpsLocation)
    val bareAddress: Document = if (project.has("address"))
      project.address[Document]
    else
      Document.parse("{\"line1\": \"...\", \"line2\": \"...\", \"line3\": \"...\", " +
        "\"postal_code\": \"...\", \"country\": {\"name\": \"United States\", \"code\": \"US\"}," +
        "\"state\": {\"name\": \"California\", \"code\": \"CA\"}}")
    val address = new Document("editable", editable).append("value", bareAddress)
    val bareSystemLabels: Seq[String] = if (project.has("system_labels"))
      project.system_labels[Many[String]]
    else
      Seq.empty[String]
    val systemLabels = new Document("editable", false).append("value", bareSystemLabels.asJava)
    val description = new Document("editable", editable).append("value", project.description[String])
    val status = new Document("editable", false).append("value", project.status[String])
    val name = new Document("editable", editable).append("value", project.name[String])
    val projectDoc = new Document("name", name).append("description", description).append("gps_location", gpsLocation).
        append("address", address).append("status", status).append("system_labels", systemLabels)
    projectDoc.toJson
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val projectRecord: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val user: DynDoc = getUser(request)
      val projectIsEditable = isEditable(projectRecord, user)
      response.getWriter.print(project2json(projectRecord, projectIsEditable))
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