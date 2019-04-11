package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class ActivityAssignments extends HttpServlet with HttpUtils {

  private def activityAssignments(activity: DynDoc): Seq[Document] = {
    Seq("Principal", "Finance", "Admin", "Lead", "Member").map(indRole => {
      new Document("_id", activity._id[ObjectId].toString).append("name", activity.name[String]).
        append("role", activity.role[String]).append("organization_name", "Some Organization").
        append("individual_role", indRole).append("person_name", "Some Person").
        append("person_id", "000000000000000000000000").append("organization_id", "000000000000000000000000").
        append("doc_access", "SomeCategory")
    })
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      val optProjectOid = parameters.get("project_id").map(new ObjectId(_))
      val optPhaseOid = parameters.get("phase_id").map(new ObjectId(_))
      val activities = (optPhaseOid, optProjectOid) match {
        case (Some(phaseOid), _) => PhaseApi.allActivities(phaseOid)
        case (None, Some(projectOid)) => ProjectApi.allActivities(projectOid)
        case _ => Nil: Seq[DynDoc]
      }
      val assignments = activities.flatMap(activityAssignments)

      val assignmentList = assignments.map(bson2json).mkString("[", ", ", "]")
      response.getWriter.print(assignmentList)
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