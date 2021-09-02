package com.buildwhiz.baf3

import com.buildwhiz.baf2.{DocumentApi, PersonApi, ProjectApi}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class PersonDelete extends HttpServlet with HttpUtils with MailUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val user: DynDoc = getUser(request)
      if (!PersonApi.isBuildWhizAdmin(Right(user)))
        throw new IllegalArgumentException("Not permitted")

      val personOid = new ObjectId(parameters("person_id"))
      val deletedPersonRecord = PersonApi.personById(personOid)
      val deletedPersonName = PersonApi.fullName(deletedPersonRecord)

      PersonApi.delete30(personOid)

      BWLogger.audit(getClass.getName, request.getMethod, s"deleted person '$deletedPersonName'", request)

      response.getWriter.print(successJson())
      response.setContentType("application/json")
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
