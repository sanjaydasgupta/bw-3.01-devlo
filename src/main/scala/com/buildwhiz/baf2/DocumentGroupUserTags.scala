package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class DocumentGroupUserTags extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val userTagCount = tagsFetch(request, response)
    BWLogger.log(getClass.getName, request.getMethod,
      s"EXIT-OK ($userTagCount)", request)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val userTagCount = tagsFetch(request, response)
    BWLogger.log(getClass.getName, request.getMethod,
      s"EXIT-OK ($userTagCount)", request)
  }

  def tagsFetch(request: HttpServletRequest, response: HttpServletResponse): Int = {
    val parameters = getParameterMap(request)
    try {
      val user: DynDoc = getUser(request)
      val freshUserRecord: DynDoc = PersonApi.personById(user._id[ObjectId])

      val docIds: Seq[String] = (if (request.getMethod == "GET") {
        parameters("document_ids")
      } else {
        getStreamData(request)
      }).split(",").map(_.trim)

      val docOids: Seq[ObjectId] = docIds.map(id => new ObjectId(id))

      val allUserTagInfo = PersonApi.documentTags(freshUserRecord)

      val documentCountsByTagName: Seq[(String, Int)] = allUserTagInfo.map(tagInfo => {
        (tagInfo.name[String], docOids.map(oid => tagInfo.document_ids[Many[ObjectId]].contains(oid)).count(b => b))
      })

      val statusByTagName: Seq[(String, String)] = documentCountsByTagName.map(count => {(count._1,
        if (count._2 == 0)
          "none"
        else if (count._2 == docOids.length)
          "all"
        else
          "some"
      )
      }).sortBy(_._1)

      val userTagsStatus: Seq[Document] = statusByTagName.map(p => new Document("name", p._1).append("scope", p._2))
      val userTagsJsonString = userTagsStatus.map(bson2json).mkString("[", ", ", "]")

      response.getWriter.println(userTagsJsonString)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      userTagsStatus.length
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}
