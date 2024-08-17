package com.buildwhiz.baf3

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class PersonaSet extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {

      val parameterMap = getParameterMap(request)

      val user: DynDoc = getUser(request)
      if (!PersonApi.isBuildWhizAdmin(Right(user)))
        throw new IllegalArgumentException("Not permitted")
      val personOid = new ObjectId(parameterMap("person_id"))
      val persona = PersonApi.personById(personOid)
      setPersona(persona.asDoc, request)

      response.getWriter.print(successJson())
      response.setContentType("application/json")
      val message = s"Set persona to ${PersonApi.fullName(persona)}"
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        reportFatalException(t, getClass.getName, request, response)
    }
  }

}