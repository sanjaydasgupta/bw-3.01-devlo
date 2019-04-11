package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId
import org.bson.Document

class DocumentGroupSystemTags extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val systemTagCount = tagsFetch(request, response)
    BWLogger.log(getClass.getName, request.getMethod,
      s"EXIT-OK ($systemTagCount)", request)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val systemTagCount = tagsFetch(request, response)
    BWLogger.log(getClass.getName, request.getMethod,
      s"EXIT-OK ($systemTagCount)", request)
  }

  def tagsFetch(request: HttpServletRequest, response: HttpServletResponse): Int = {
    val parameters = getParameterMap(request)
    try {
      val docIds: Seq[String] = (if (request.getMethod == "GET") {
        parameters("document_ids")
      } else {
        getStreamData(request)
      }).split(",").map(_.trim).filter(_.nonEmpty)

      val docOids: Seq[ObjectId] = docIds.map(id => new ObjectId(id.trim))

      val documentRecords: Seq[DynDoc] = BWMongoDB3.document_master.find(Map("_id" -> Map("$in" -> docOids)))
      val projectOids = documentRecords.map(_.project_id[ObjectId])
      val commonProjectOid = projectOids.reduceLeft((a, b) => if (a == b) a else b)
      if (commonProjectOid != projectOids.head)
        throw new IllegalArgumentException("Documents not from same project")

      val allSystemTagNames: Seq[String] = ProjectApi.documentTags(ProjectApi.projectById(commonProjectOid)).
          map(_.name[String])
      val systemTagsByDocument: Seq[Seq[String]] =
          documentRecords.map(docRecord => DocumentApi.getSystemTags(docRecord))
      val documentCountsByTagName: Seq[(String, Int)] = allSystemTagNames.map(tagName =>
        (tagName, systemTagsByDocument.map(_.contains(tagName)).count(b => b))
      )
      val statusByTagName: Seq[(String, String)] = documentCountsByTagName.map(count => {(count._1,
        if (count._2 == 0)
          "none"
        else if (count._2 == docOids.length)
          "all"
        else
          "some"
        )
      }).sortBy(_._1)

      val systemTagsStatus: Seq[Document] = statusByTagName.map(p => new Document("name", p._1).append("scope", p._2))
      val systemTagsJsonString = systemTagsStatus.map(bson2json).mkString("[", ", ", "]")

      response.getWriter.println(systemTagsJsonString)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      systemTagsStatus.length
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}

