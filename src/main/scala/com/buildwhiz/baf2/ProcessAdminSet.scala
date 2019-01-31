package com.buildwhiz.baf2

import com.buildwhiz.api.Project
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class ProcessAdminSet extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val personOid = new ObjectId(parameters("person_id"))
      if (!PersonApi.exists(personOid))
        throw new IllegalArgumentException(s"Bad person id: '$personOid'")
      val processOid = new ObjectId(parameters("process_id"))
      val theProcess: DynDoc = ProcessApi.processById(processOid)
      val parentPhase: DynDoc = ProcessApi.parentPhase(processOid)
      val parentProject: DynDoc = PhaseApi.parentProject(parentPhase._id[ObjectId])
      val user: DynDoc = getUser(request)
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      val isAdmin = freshUserRecord.roles[Many[String]].contains("BW-Admin")
      if (!isAdmin && freshUserRecord._id[ObjectId] != parentProject.admin_person_id[ObjectId] &&
        freshUserRecord._id[ObjectId] != parentPhase.admin_person_id[ObjectId])
        throw new IllegalArgumentException("Not permitted")
      val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> processOid),
        Map("$set" -> Map(s"admin_person_id" -> personOid)))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      Project.renewUserAssociations(request, Some(parentProject._id[ObjectId]))
      response.setStatus(HttpServletResponse.SC_OK)
      val message = s"Set manager of process '${theProcess.name[String]}' ($processOid)"
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
