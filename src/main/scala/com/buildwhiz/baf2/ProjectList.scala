package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class ProjectList extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      val projects = ProjectList.getList(userOid, request)
      response.getWriter.print(projects.map(project2json(_, userOid)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${projects.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  def project2json(project: DynDoc, userPersonOid: ObjectId): String = {
    val processes: Seq[DynDoc] = ProjectApi.allPhases(project).flatMap(phase => PhaseApi.allProcesses(phase))

    val projectDocument = new Document("name", project.name[String]).append("_id", project._id[ObjectId].toString).
        append("status", project.status[String]).append("display_status", ProjectApi.displayStatus(project)).
        append("is_managed", project.admin_person_id[ObjectId] == userPersonOid).
        append("admin_person_id", project.admin_person_id[ObjectId].toString).
        append("docsUrl", s"docs?project_id=${project._id[ObjectId]}").
        append("can_end", !processes.exists(_.status[String] == "running") && project.status[String] != "ended")
    bson2json(projectDocument)
  }

}

object ProjectList extends HttpUtils {

  def getList(userOid: ObjectId, request: HttpServletRequest, doLog: Boolean = false): Seq[DynDoc] = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> userOid)).head
    val isAdmin = PersonApi.isBuildWhizAdmin(Right(freshUserRecord))
    val projects: Seq[DynDoc] = if (isAdmin) {
      BWMongoDB3.projects.find()
    } else {
      ProjectApi.projectsByUser(userOid)
    }
    BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${projects.length})", request)
    projects
  }
}