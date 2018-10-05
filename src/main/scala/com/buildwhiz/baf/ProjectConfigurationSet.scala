package com.buildwhiz.baf

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class ProjectConfigurationSet extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val user: DynDoc = getUser(request)
      if (user._id[ObjectId] != project.admin_person_id[ObjectId])
        throw new IllegalArgumentException("Not permitted")
      val postData: DynDoc = Document.parse(getStreamData(request))
      val description = postData.description[String]
      val assignedRoles: Seq[DynDoc] = postData.assigned_roles[Many[Document]]
      val roles: Seq[Document] = assignedRoles.map(r => {
        val person_id = r.person_id[String]
        val personOid = new ObjectId(person_id)
        if (BWMongoDB3.persons.find(Map("_id" -> personOid)).isEmpty)
          throw new IllegalArgumentException(s"invalid person-id '$person_id'")
        val roleName = r.role_name[String]
        Map("person_id" -> personOid, "role_name" -> roleName)
      })
      val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid),
        Map("$set" -> Map("description" -> description, "assigned_roles" -> roles)))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      val event = s"Updated project '${project.name[String]}' configuration"
      BWLogger.audit(getClass.getName, request.getMethod, event, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod,
            s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}
