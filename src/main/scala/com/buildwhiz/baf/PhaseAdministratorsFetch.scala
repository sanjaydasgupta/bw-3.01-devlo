package com.buildwhiz.baf

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.BWLogger
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class PhaseAdministratorsFetch extends HttpServlet {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    //
    // ToDo: Use parameters to determine possible phase-managers
    //
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val roleRegex = s".*Project-Management.*"
      val candidates: Seq[DynDoc] = BWMongoDB3.persons.find(Map("roles" -> Map("$regex" -> roleRegex)))
      val managers: Seq[Document] = candidates.map(candidate => {
        val name = s"${candidate.first_name[String]} ${candidate.last_name[String]}"
        Map("_id" -> candidate._id[ObjectId].toString, "name" -> name)
      })
      response.getWriter.print(managers.map(_.toJson).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${managers.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}