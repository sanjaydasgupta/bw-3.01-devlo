package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

class PhaseStartDateTimeSet extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      val phaseOid = new ObjectId(parameters("phase_id"))
      val thePhase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val theProject: DynDoc = BWMongoDB3.projects.find(Map("phase_ids" -> phaseOid)).head
      if (userOid != thePhase.admin_person_id[ObjectId] && userOid != theProject.admin_person_id[ObjectId])
        throw new IllegalArgumentException("No permission")
      if (thePhase.status[String] != "defined")
        throw new IllegalArgumentException("Wrong state")
      val dateTime = parameters("datetime").toLong
      val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid),
        Map("$set" -> Map(s"timestamps.planned_start" -> dateTime)))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      response.setStatus(HttpServletResponse.SC_OK)
      val phaseLog = s"'${thePhase.name[String]}' ($phaseOid)"
      BWLogger.audit(getClass.getName, "doPost", s"""Set start-date of phase $phaseLog""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
