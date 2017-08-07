package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

class PhaseEnd extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val phaseId = parameters("phase_id")
      val thePhase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> new ObjectId(phaseId))).head
      val isHealthy = OwnedPhases.healthy(thePhase)
      if (isHealthy) {
        BWLogger.log(getClass.getName, "doPost", "EXIT-OK process healthy, made no changes", request)
      } else {
        val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> new ObjectId(phaseId)),
          Map("$set" -> Map("status" -> "ended")))
        if (updateResult.getModifiedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
        val phaseLog = s"'${thePhase.name[String]}' ($phaseId)"
        BWLogger.audit(getClass.getName, "doPost", s"Ended phase $phaseLog", request)
      }
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
