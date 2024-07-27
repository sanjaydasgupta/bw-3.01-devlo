package com.buildwhiz.baf3

import com.buildwhiz.baf2.PhaseApi
import com.buildwhiz.infra.{BWMongoDB3, BWMongoDBLib}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class PhaseDelete extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    response.setContentType("application/json")
    try {
      val parameters = getParameterMap(request)
      val phaseOid = new ObjectId(parameters("phase_id"))
      val (inLibrary, db) = parameters.get("in_library") match {
        case Some(lib) if lib.toBoolean => (true, BWMongoDBLib)
        case _ => (false, BWMongoDB3)
      }
      val thePhase = PhaseApi.phaseById(phaseOid, db)
      val message = PhaseApi.delete(thePhase, request, db)
      response.getWriter.println(successJson())
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable => reportFatalException(t, getClass.getName, request, response)
    }
  }

}