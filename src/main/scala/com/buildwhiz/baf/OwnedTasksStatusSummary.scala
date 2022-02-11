package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

class OwnedTasksStatusSummary extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
      val projectOids: Seq[ObjectId] = person.project_ids[Many[ObjectId]]
      val projects: Seq[DynDoc] = projectOids.flatMap(oid => BWMongoDB3.projects.find(Map("_id" -> oid)))
      val phaseOids: Seq[ObjectId] = projects.flatMap(_.process_ids[Many[ObjectId]])
      val phases: Seq[DynDoc] = phaseOids.flatMap(oid => BWMongoDB3.processes.find(Map("_id" -> oid)))
      val activityOids = phases.flatMap(_.activity_ids[Many[ObjectId]])
      val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityOids)))
      val allActions: Seq[DynDoc] = activities.flatMap(_.actions[Many[Document]])
      val actions = allActions.filter(_.assignee_person_id[ObjectId] == personOid)
      val countsByStatus = actions.groupBy(_.status[String]).map(t => (t._1, t._2.size))
      val statusDocument = new Document(countsByStatus)
      response.getWriter.print(statusDocument.toJson)
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