package com.buildwhiz.baf

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId
import org.bson.Document

class ActionRolesFetch extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val activityOid = new ObjectId(parameters("activity_id"))
      val actionType = parameters("action_type")
      val resultRoles = ActionRolesFetch.roles(activityOid, actionType)
      response.getWriter.println(resultRoles.mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object ActionRolesFetch {

  def roles(activityOid: ObjectId, actionType: String): Seq[String] = {
    //
    // ToDo: use action_type to select roles
    //
    if (!actionType.matches("review|prerequisite|main"))
      throw new IllegalArgumentException(s"bad action type: '$actionType'")
    val theActivity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).head
    val allTheActions: Seq[DynDoc] = theActivity.actions[Many[Document]]
    val theMainAction: DynDoc = allTheActions.filter(_.`type`[String] == "main").head
    val resultRoles = if (theMainAction.has("assignee_role")) {
      val mainActionRole = theMainAction.assignee_role[String]
      Seq(mainActionRole, s"$mainActionRole-$actionType")
    } else
      Seq(s"???-$actionType")
    resultRoles
  }
}
