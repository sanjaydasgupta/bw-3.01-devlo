package com.buildwhiz.api

import java.net.URI
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.amazonaws.services.s3.model.S3ObjectSummary
import com.buildwhiz.baf.OwnedProjects
import com.buildwhiz.infra.AmazonS3
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.BWLogger
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class Project extends HttpServlet with RestUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doPost()", "ENTRY", request)
    try {
      val postData = getStreamData(request)
      BWLogger.log(getClass.getName, "doPost(): postData", postData)
      val project: DynDoc = Document.parse(postData)
      if (!(project has "name"))
        throw new IllegalArgumentException("No 'name' found")
      if (!(project has "admin_person_id"))
        throw new IllegalArgumentException("No 'admin_person_id' found")
      if (!project.admin_person_id[AnyRef].isInstanceOf[ObjectId])
        throw new IllegalArgumentException("Type of 'admin_person_id' not ObjectId")
      project.description = s"This is the description of the '${project.name[String]}' project. " * 5
      val latLong: DynDoc = Map("latitude" -> 37.7857971, "longitude" -> -122.4142195)
      project.gps_location = latLong.asDoc
      val address: DynDoc = Map("line1" -> "First line of the address", "line2" -> "Second line of the address",
        "line3" -> "Third line of the address", "state" -> Map("name" -> "California", "code" -> "CA"),
        "country" -> Map("name" -> "United States", "code" -> "US"), "postal_code" -> "94102")
      project.address = address.asDoc
      project.phase_ids = new java.util.ArrayList[ObjectId]
      project.assigned_roles = new java.util.ArrayList[ObjectId]
      project.system_labels = new java.util.ArrayList[ObjectId]
      project.timestamps = new Document("created", System.currentTimeMillis)
      project.status = "defined" // Initial status on creation
      project.process_ids = new java.util.ArrayList[ObjectId]
      BWMongoDB3.projects.insertOne(project.asDoc)

      BWMongoDB3.persons.updateOne(Map("_id" -> project.admin_person_id[ObjectId]),
        Map("$addToSet" -> Map("project_ids" -> project._id[ObjectId])))

      val adminPersonOid = project.admin_person_id[ObjectId]
      response.getWriter.println(bson2json(OwnedProjects.processProject(project, adminPersonOid).asDoc))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.audit(getClass.getName, "doPost", s"""Added Project '${project.name[String]}'""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  override def doPut(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestPut(request, response, "Project", "projects")
  }

  override def doDelete(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doDelete()", s"ENTRY", request)
    try {
      val uriParts = new URI(request.getRequestURI.replace("[", "%5B").replace("]", "%5D")).getPath.split("/").toSeq.reverse
      val projectOid = uriParts match {
        case idString +: "Project" +: _ => new ObjectId(idString)
        case _ => throw new IllegalArgumentException("Id not found")
      }
      val theProject: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val phaseOids: Seq[ObjectId] = theProject.process_ids[Many[ObjectId]]
      val phases: Seq[DynDoc] = BWMongoDB3.processes.find(Map("_id" -> Map("$in" -> phaseOids)))
      val activityOids: Seq[ObjectId] = phases.flatMap(_.activity_ids[Many[ObjectId]])
      BWMongoDB3.activities.deleteMany(Map("_id" -> Map("$in" -> activityOids)))
      BWMongoDB3.processes.deleteMany(Map("_id" -> Map("$in" -> phaseOids)))
      BWMongoDB3.mails.deleteMany(Map("project_id" -> projectOid))
      BWMongoDB3.persons.updateMany(Map.empty[String, Any], Map("$pull" -> Map("project_ids" -> projectOid)))
      BWMongoDB3.projects.deleteOne(Map("_id" -> projectOid))
      // Delete project's documents
      val objectSummaries: Seq[S3ObjectSummary] = AmazonS3.listObjects(projectOid.toString).getObjectSummaries.asScala
      for (summary <- objectSummaries) {
        AmazonS3.deleteObject(summary.getKey)
      }
      val projectNameAndId = s"""${theProject.name[String]} (${theProject._id[ObjectId]})"""
      BWLogger.audit(getClass.getName, "doDelete", s"""Deleted Project '$projectNameAndId'""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doDelete()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestGet(request, response, "Project", "projects")
  }

}

object Project {

  def allPhaseOids(project: DynDoc): Seq[ObjectId] = project.process_ids[Many[ObjectId]]
  def allPhases(project: DynDoc): Seq[DynDoc] = allPhaseOids(project).
    flatMap(phaseOid => BWMongoDB3.processes.find(Map("_id" -> phaseOid)).headOption)

  def allActivities(project: DynDoc): Seq[DynDoc] = allPhases(project).flatMap(Phase.allActivities)

  def allActions(project: DynDoc): Seq[DynDoc] = allActivities(project).flatMap(Activity.allActions)

  def projectLevelUsers(project: DynDoc): Seq[ObjectId] = {
    if (project.has("assigned_roles")) {
      (project.admin_person_id[ObjectId] +:
          project.assigned_roles[Many[Document]].map(_.person_id[ObjectId])).distinct
    } else {
      Seq(project.admin_person_id[ObjectId])
    }
  }

  def allProjectUsers(project: DynDoc): Seq[ObjectId] = {
    val phaseUsers = allPhases(project).flatMap(Phase.allPhaseUsers)
    (projectLevelUsers(project) ++ phaseUsers).distinct
  }

  def renewUserAssociations(request: HttpServletRequest, projectOidOption: Option[ObjectId] = None): Unit = {

    projectOidOption match {
      case None => BWMongoDB3.projects.find().foreach(proj => renewUserAssociations(request, Some(proj._id[ObjectId])))

      case Some(projectOid) =>
        val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
        val userOids: Seq[ObjectId] = allProjectUsers(project)

        val updateResult = BWMongoDB3.persons.updateMany(Map("_id" -> Map("$in" -> userOids)),
            Map("$addToSet" -> Map("project_ids" -> projectOid)))
        val updateResult2 = BWMongoDB3.persons.updateMany(Map("_id" -> Map("$nin" -> userOids)),
          Map("$pull" -> Map("project_ids" -> projectOid)))

        if (updateResult.getMatchedCount + updateResult2.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB error: $updateResult, $updateResult2")

        val userNames = userOids.map(userOid => {
          val user: DynDoc = BWMongoDB3.persons.find(Map("_id" -> userOid)).head
          s"${user.first_name[String]} ${user.last_name[String]}"
        })
        val message = s"""Project '${project.name[String]}' linked to users ${userNames.mkString(",")}"""
        BWLogger.audit(getClass.getName, "renewUserAssociation", message, request)
    }

  }

  def canEnd(project: DynDoc): Boolean = {
    val projectAlreadyEnded = project.status[String] == "ended"
    val phases: Seq[DynDoc] = BWMongoDB3.processes.find(Map("_id" -> Map("$in" -> project.process_ids[Many[ObjectId]])))
    !phases.exists(_.status[String] == "running") && !projectAlreadyEnded
  }

  def hasRoleInProject(personOid: ObjectId, project: DynDoc): Boolean =
      project.admin_person_id[ObjectId] == personOid ||
      project.assigned_roles[Many[Document]].exists(_.person_id[ObjectId] == personOid)

  def isProjectManager(personOid: ObjectId, project: DynDoc): Boolean =
      project.admin_person_id[ObjectId] == personOid || project.assigned_roles[Many[Document]].
      exists(proj => proj.person_id[ObjectId] == personOid && proj.role_name[String] == "Project-Manager")

  def projectsByUser(personOid: ObjectId): Seq[DynDoc] = {
    val projects: Seq[DynDoc] = BWMongoDB3.projects.find()
    projects.filter(project => hasRoleInProject(personOid, project))
  }

}