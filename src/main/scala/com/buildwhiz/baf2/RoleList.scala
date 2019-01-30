package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc
import DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import org.bson.types.ObjectId
import org.bson.Document

class RoleList extends HttpServlet with HttpUtils {

  private def getRoles(request: HttpServletRequest): Seq[String] = {

    val parameters = getParameterMap(request)
    def oid(id: String) = new ObjectId(id)

    val activities: (String, Seq[DynDoc]) = if (parameters.contains("process_id")) {
      ("process_id", ProcessApi.allActivities(oid(parameters("process_id"))))
    } else if (parameters.contains("phase_id")) {
      ("phase_id", PhaseApi.allActivities(oid(parameters("phase_id"))))
    } else if (parameters.contains("project_id")) {
      ("project_id", ProjectApi.allActivities(oid(parameters("project_id"))))
    } else {
      ("", Seq.empty[DynDoc])
    }

    BWLogger.log(getClass.getName, "getRoles",
      s"**** ${activities._1}: ${activities._2.length} ****", request)

    val results = activities._2.map(_.actions[Many[Document]].head.assignee_role[String])
    BWLogger.log(getClass.getName, "getRoles", s"**** ${results.length} ****", request)
    results
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val resultRoles = getRoles(request).map(r => s""""$r"""")
      BWLogger.log(getClass.getName, request.getMethod, s"**** ${resultRoles.length} ****", request)
      response.getWriter.println(resultRoles.mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", s"EXIT-OK (${resultRoles.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object RoleTest extends App {
  val roles = ProcessApi.allActivities(new ObjectId("5c51959d3c364b110df737f1"))
  println(roles.length)
}
