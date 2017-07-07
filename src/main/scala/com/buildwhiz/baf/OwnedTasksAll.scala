package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

class OwnedTasksAll extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
      val projectOids: Seq[ObjectId] = person.project_ids[Many[ObjectId]]
      val projects: Seq[DynDoc] = projectOids.flatMap(oid => BWMongoDB3.projects.find(Map("_id" -> oid)))
      val phaseOids: Seq[ObjectId] = projects.flatMap(_.phase_ids[Many[ObjectId]])
      val phases: Seq[DynDoc] = phaseOids.flatMap(oid => BWMongoDB3.phases.find(Map("_id" -> oid)))
      val activityOids = phases.flatMap(_.activity_ids[Many[ObjectId]])
      val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityOids)))
      val allActions: Seq[DynDoc] = activities.flatMap(_.actions[Many[Document]])
      val actions = allActions.filter(_.assignee_person_id[ObjectId] == personOid)
      writer.print(actions.map(a => bson2json(a.asDoc)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}