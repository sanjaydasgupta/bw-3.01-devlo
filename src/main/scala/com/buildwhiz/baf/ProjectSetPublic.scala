package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

class ProjectSetPublic extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val public = parameters("public").toBoolean
      val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid), Map("$set" -> Map("public" -> public)))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      val theProject: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val projectLog = s"'${theProject.name[String]}' (${theProject._id[ObjectId]})"
      BWLogger.audit(getClass.getName, request.getMethod, s"""Set public project $projectLog""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}
