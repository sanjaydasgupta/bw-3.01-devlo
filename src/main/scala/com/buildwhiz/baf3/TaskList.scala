package com.buildwhiz.baf3

import com.buildwhiz.baf2.PhaseApi
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class TaskList extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    val phaseOid = new ObjectId(parameters("phase_id"))
    try {
      val activities: Seq[Document] = PhaseApi.allActivities(phaseOid).
          map(activity => Map("_id" -> activity._id[ObjectId], "name" -> activity.name[String]))

      response.getWriter.print(activities.map(_.toJson).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${activities.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}

