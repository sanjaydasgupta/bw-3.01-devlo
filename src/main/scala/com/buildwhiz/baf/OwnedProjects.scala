package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3, Utils}
import org.bson.types.ObjectId
import org.camunda.bpm.engine.ProcessEngines

import scala.collection.JavaConversions._

class OwnedProjects extends HttpServlet with Utils {
  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
      val projectOids: Seq[ObjectId] = person.project_ids[ObjectIdList]
      val projects: Seq[DynDoc] = BWMongoDB3.projects.find(Map("_id" -> Map("$in" -> projectOids))).toSeq
      val augmentedProjects = projects.map(project => bson2json(OwnedProjects.processProject(project, personOid).asDoc))
      writer.print(augmentedProjects.mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}

object OwnedProjects {

  def processProject(project: DynDoc, personOid: ObjectId): DynDoc = {
    def canEnd(project: DynDoc): Boolean = {
      val phases: Seq[DynDoc] = BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> project.phase_ids[ObjectIdList]))).toSeq
      !phases.exists(_.status[String] == "running")
    }

    project.is_managed = project.admin_person_id[ObjectId] == personOid
    project.can_end = canEnd(project)
    val phaseOids = project.phase_ids[ObjectIdList]
    val phases: Seq[DynDoc] = BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> phaseOids))).toSeq
    val activityIds: ObjectIdList = phases.flatMap(_.activity_ids[ObjectIdList])
    val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityIds))).toSeq
    val actions: Seq[DynDoc] = activities.flatMap(_.actions[DocumentList])
    if (actions.exists(action => action.status[String] == "waiting" && action.assignee_person_id[ObjectId] == personOid))
      project.display_status = "waiting"
    else if (actions.exists(action => action.status[String] == "waiting"))
      project.display_status = "waiting2"
    else
      project.display_status = project.status[String]
    project.asDoc.remove("phase_ids")
    project
  }

}