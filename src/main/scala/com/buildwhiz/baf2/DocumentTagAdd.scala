package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils, TagLogicProcessor}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class DocumentTagAdd extends HttpServlet with HttpUtils {

  private def addSystemTag(tagName: String, projectOid: ObjectId, optLogic: Option[String], request: HttpServletRequest): Unit = {
    val project: DynDoc = ProjectApi.projectById(projectOid)
    ProjectApi.addDocumentTag(tagName, project, optLogic, request)
  }

  private def addUserTag(tagName: String, optLogic: Option[String], request: HttpServletRequest): Unit = {
    val user: DynDoc = getUser(request)
    PersonApi.addDocumentTag(user._id[ObjectId], tagName, optLogic)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val tagName: String = parameters("tag_name")
      if (!TagLogicProcessor.labelIsValid(tagName))
        throw new IllegalArgumentException(s"Bad tag name '$tagName'")

      val tagLogic: Option[String] = parameters.get("logic").map(TagLogicProcessor.logicIsValid) match {
        case None => None
        case Some(true) => Some(parameters("logic"))
        case Some(false) => throw new IllegalArgumentException("Bad logic expression")
      }

      val tagType = parameters("tag_type")

      if (tagType == "user") {
        addUserTag(tagName, tagLogic, request)
        BWLogger.audit(getClass.getName, request.getMethod, s"Added user document-tag '$tagName'", request)
      } else if (tagType == "system") {
        val parameters = getParameterMap(request)
        val projectOid = new ObjectId(parameters("project_id"))
        addSystemTag(tagName, projectOid, tagLogic, request)
        BWLogger.audit(getClass.getName, request.getMethod, s"Added system document-tag '$tagName'", request)
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
