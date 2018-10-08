package com.buildwhiz.baf

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import com.buildwhiz.api.Project

class ProjectConfigurationSet extends HttpServlet with HttpUtils {

  private def manageEffects(request: HttpServletRequest, existing: Seq[DynDoc], updated: Seq[DynDoc],
        projectOid: ObjectId): Unit = {
    val existingSet = existing.map(d => (d.person_id[ObjectId], d.role_name[String])).toSet
    val updatedSet = updated.map(d => (d.person_id[ObjectId], d.role_name[String])).toSet
    val removedRoles = existingSet.diff(updatedSet)
    val addedRoles = updatedSet.diff(existingSet)
    //
    // ToDo: send emails
    //
    Project.renewUserAssociations(request, Some(projectOid))
  }

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
      val updatedRoles: Seq[DynDoc] = assignedRoles.map(r => {
        val person_id = r.person_id[String]
        val personOid = new ObjectId(person_id)
        if (BWMongoDB3.persons.find(Map("_id" -> personOid)).isEmpty)
          throw new IllegalArgumentException(s"invalid person-id '$person_id'")
        val roleName = r.role_name[String]
        if (!ProjectConfigurationFetch.standardRoleNames.contains(roleName))
          throw new IllegalArgumentException(s"invalid role-name '$roleName'")
        Map("person_id" -> personOid, "role_name" -> roleName)
      })
      val existingRoles: Seq[Document] = if (project.has("assigned_roles"))
        project.assigned_roles[Many[Document]] else Seq.empty[Document]
      val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid),
        Map("$set" -> Map("description" -> description, "assigned_roles" -> updatedRoles)))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      manageEffects(request, existingRoles, updatedRoles, projectOid)
      response.setStatus(HttpServletResponse.SC_OK)
      val rolesMsg = updatedRoles.map(role => {
        val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> role.person_id[ObjectId])).head
        val name = s"${person.first_name[String]} ${person.last_name[String]}"
        val roleName = role.role_name[String]
        s"$roleName: $name"
      }).mkString("roles=\'", ", ", "\'")
      val descMsg = s"description='$description'"
      val message = s"Updated project '${project.name[String]}' with $rolesMsg and $descMsg"
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod,
            s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}
