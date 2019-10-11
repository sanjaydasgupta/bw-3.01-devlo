package com.buildwhiz.baf2

import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class PersonVcard extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val person = PersonApi.personById(personOid)
      response.getWriter.print(PersonApi.vCard(Right(person)))
      response.setContentType("text/directory")
      val fileName = s"${person.first_name[String]}-${person.last_name[String]}".toList.
        filter(c => c.isLetter || c.isDigit || c == '-').mkString
      response.setHeader("Content-Disposition", s"attachment; filename=$fileName.vcf")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}