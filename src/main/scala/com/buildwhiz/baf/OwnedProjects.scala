package com.buildwhiz.baf

import com.buildwhiz.api.Project
import com.buildwhiz.baf2.PersonApi
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

class OwnedProjects extends HttpServlet with HttpUtils {
  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    //val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val personOid = user._id[ObjectId]
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
      val isAdmin = PersonApi.isBuildWhizAdmin(Right(freshUserRecord))
      val query: Document = if (isAdmin) {
        Map.empty[String, Any]
      } else {
        val projectOids: Seq[ObjectId] = freshUserRecord.project_ids[Many[ObjectId]]
        Map("_id" -> Map("$in" -> projectOids))
      }
      val projects: Seq[DynDoc] = BWMongoDB3.projects.find(query)
      val augmentedProjects = projects.map(project => bson2json(OwnedProjects.processProject(project, personOid).asDoc))
      response.getWriter.print(augmentedProjects.mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object OwnedProjects {

  def processProject(project: DynDoc, personOid: ObjectId): DynDoc = {
    def canEnd(project: DynDoc): Boolean = {
      val projectAlreadyEnded = project.status[String] == "ended"
      val phases: Seq[DynDoc] = BWMongoDB3.processes.find(Map("_id" -> Map("$in" -> project.process_ids[Many[ObjectId]])))
      !phases.exists(_.status[String] == "running") && !projectAlreadyEnded
    }

    project.is_managed = project.admin_person_id[ObjectId] == personOid
    project.can_end = canEnd(project)
    val phases: Seq[DynDoc] = Project.allPhases(project)
    val actions: Seq[DynDoc] = Project.allActions(project)
    if (actions.exists(action => action.status[String] == "waiting" && action.assignee_person_id[ObjectId] == personOid))
      project.display_status = "waiting"
    else if (actions.exists(action => action.status[String] == "waiting"))
      project.display_status = "waiting2"
    else if (!phases.exists(_.status[String] == "running") && project.status[String] == "running")
      project.display_status = "idle"
    else
      project.display_status = project.status[String]
    project.docsUrl = s"docs?project_id=${project._id[ObjectId]}"
    project.asDoc.remove("process_ids")
    project
  }

}