package com.buildwhiz.baf3

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._
import com.buildwhiz.baf2.{PersonApi, PhaseApi, ProjectApi}

class ProjectList extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      val projects = ProjectList.getList(userOid, request)
      val projectsInfo: Many[Document] = projects.map(projectInfo).asJava
      val canCreateNewProject = PersonApi.isBuildWhizAdmin(Left(userOid))
      val result = new Document("can_create_new_project", canCreateNewProject).append("projects", projectsInfo)
      response.getWriter.print(result.toJson)
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

  def projectInfo(project: DynDoc): Document = {
    val phases: Seq[DynDoc] = ProjectApi.allPhases(project).map(phase =>
        Map("name" -> phase.name[String], "_id" -> phase._id[ObjectId].toString,
        "display_status" -> PhaseApi.displayStatus(phase), "alert_count" -> "3", "rfi_count" -> "4",
          "issue_count" -> 7, "discussion_count" -> "2", "budget" -> "1.5 MM", "expenditure" -> "350,500"))
    Map("name" -> project.name[String], "_id" -> project._id[ObjectId].toString,
        "display_status" -> ProjectApi.displayStatus(project), "address" -> project.address[Document],
        "phases" -> phases.map(_.asDoc).asJava)
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