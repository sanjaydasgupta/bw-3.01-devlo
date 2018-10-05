package com.buildwhiz.baf

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, BpmnUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class PhaseAdministratorsFetch extends HttpServlet with BpmnUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    //
    // ToDo: Use phase_id to determine possible phase-managers
    //
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val managers: Seq[Document] = Seq("Sourav", "Gouri", "Sanjay").map(fn => {
        val person: DynDoc = BWMongoDB3.persons.find(Map("first_name" -> fn)).head
        val name = s"${person.first_name[String]} ${person.last_name[String]}"
        new Document("_id", person._id[ObjectId].toString).append("name", name)
      })
      response.getWriter.print(managers.map(_.toJson).mkString("[", ", ", "]"))
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