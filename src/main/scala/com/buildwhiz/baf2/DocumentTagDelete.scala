package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class DocumentTagDelete extends HttpServlet with HttpUtils {

  private def deleteSystemTag(tagName: String, projectOid: ObjectId, request: HttpServletRequest): Unit = {
    val project: DynDoc = ProjectApi.projectById(projectOid)
    ProjectApi.deleteDocumentTag(tagName, project, request)
  }

  private def deleteUserTag(tagName: String, request: HttpServletRequest): Unit = {
    val user: DynDoc = getUser(request)
    val userOid = user._id[ObjectId]
    PersonApi.deleteDocumentTag(userOid, tagName)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val tagType = parameters("tag_type")
      val tagName: String = parameters("tag_name")
      if (tagType == "user") {
        deleteUserTag(tagName, request)
        BWLogger.audit(getClass.getName, request.getMethod, s"Deleted user document-tag '$tagName'", request)
      } else if (tagType == "system") {
        val parameters = getParameterMap(request)
        val projectOid = new ObjectId(parameters("project_id"))
        deleteSystemTag(tagName, projectOid, request)
        BWLogger.audit(getClass.getName, request.getMethod, s"Deleted system document-tag '$tagName'", request)
      } else {
        throw new IllegalArgumentException(s"Unknown tag-type: '$tagType'")
      }
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}
