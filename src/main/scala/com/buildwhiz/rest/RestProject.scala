package com.buildwhiz.rest

import java.net.URI
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.baf.OwnedProjects
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3, Utils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConversions._

class RestProject extends HttpServlet with Utils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doPost()", "ENTRY", request)
    try {
      val postData = getStreamData(request)
      BWLogger.log(getClass.getName, "doPost(): postData", postData)
      val project: DynDoc = Document.parse(postData)
      if (!(project ? "name"))
        throw new IllegalArgumentException("No 'name' found")
      if (!(project ? "admin_person_id"))
        throw new IllegalArgumentException("No 'admin_person_id' found")
      if (!project.admin_person_id[AnyRef].isInstanceOf[ObjectId])
        throw new IllegalArgumentException("Type of 'admin_person_id' not ObjectId")
      project.timestamps = new Document("created", System.currentTimeMillis)
      project.status = "defined" // Initial status on creation
      project.phase_ids = new java.util.ArrayList[ObjectId]
      BWMongoDB3.projects.insertOne(project.asDoc)

      BWMongoDB3.persons.updateOne(Map("_id" -> project.admin_person_id[ObjectId]),
        Map("$push" -> Map("project_ids" -> project._id[ObjectId])))

      val adminPersonOid = project.admin_person_id[ObjectId]
      response.getWriter.println(bson2json(OwnedProjects.processProject(project, adminPersonOid).asDoc))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doPost()", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
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
      val phaseOids: Seq[ObjectId] = theProject.phase_ids[ObjectIdList]
      val phases: Seq[DynDoc] = BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> phaseOids))).toSeq
      val activityOids: Seq[ObjectId] = phases.flatMap(_.activity_ids[ObjectIdList])
      BWMongoDB3.activities.deleteMany(Map("_id" -> Map("$in" -> activityOids)))
      BWMongoDB3.phases.deleteMany(Map("_id" -> Map("$in" -> phaseOids)))
      BWMongoDB3.mails.deleteMany(Map("project_id" -> projectOid))
      BWMongoDB3.persons.updateMany(Map.empty[String, Any], Map("$pull" -> Map("project_ids" -> projectOid)))
      BWMongoDB3.projects.deleteOne(Map("_id" -> projectOid))
      BWLogger.log(getClass.getName, "doDelete()", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doDelete()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleRestGet(request, response, "Project", "projects")
  }

}