package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class DocumentTagAdd extends HttpServlet with HttpUtils {

  private def addSystemTag(tagName: String, projectOid: ObjectId, request: HttpServletRequest): Unit = {
    val project: DynDoc = ProjectApi.projectById(projectOid)
    ProjectApi.addDocumentTag(tagName, project, request)
  }

  private def addUserTag(tagName: String, request: HttpServletRequest): Unit = {
    val user: DynDoc = getUser(request)
    val userOid = user._id[ObjectId]
    val freshUserRecord: DynDoc = PersonApi.personById(userOid)
    val userTags: Seq[String] = PersonApi.documentTags(freshUserRecord)
    if (userTags.contains(tagName))
      throw new IllegalArgumentException(s"Tag '$tagName' already exists")
    val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> user._id[ObjectId]),
      Map("$push" -> Map("labels" -> Map("name" -> tagName, "document_ids" -> Seq.empty[ObjectId]))))
    if (updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val tagType = parameters("tag_type")
      val tagName: String = parameters("tag_name")
      if (tagType == "user") {
        addUserTag(tagName, request)
        BWLogger.audit(getClass.getName, request.getMethod, s"Added user document-tag '$tagName'", request)
      } else if (tagType == "system") {
        val parameters = getParameterMap(request)
        val projectOid = new ObjectId(parameters("project_id"))
        addSystemTag(tagName, projectOid, request)
        BWLogger.audit(getClass.getName, request.getMethod, s"Added system document-tag '$tagName'", request)
      } else {
        throw new IllegalArgumentException(s"Unknown tag-type: '$tagType'")
      }
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}
