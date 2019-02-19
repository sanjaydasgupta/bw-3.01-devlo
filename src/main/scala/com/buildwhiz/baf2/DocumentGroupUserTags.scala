package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
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
      val person: DynDoc = PersonApi.personById(user._id[ObjectId])

      val docIds: Seq[String] = (if (request.getMethod == "GET") {
        parameters("document_ids")
      } else {
        getStreamData(request)
      }).split(",").map(_.trim)

      val docOids: Seq[ObjectId] = docIds.map(id => new ObjectId(id.trim))

      val documentRecords: Seq[DynDoc] = BWMongoDB3.document_master.find(Map("_id" -> Map("$in" -> docOids)))

      val docOid2UserTags: Map[ObjectId, Seq[String]] = DocumentApi.docOid2UserTags(person)
      val docOid2SystemTags: Map[ObjectId, Seq[String]] = documentRecords.
          map(docRec => (docRec._id[ObjectId], DocumentApi.getSystemTags(docRec))).toMap

      val allUserTags: Seq[String] = docOids.flatMap(oid =>
        if (docOid2UserTags.contains(oid) || docOid2SystemTags.contains(oid)) {
          val userTags = if (docOid2UserTags.contains(oid)) docOid2UserTags(oid) else Seq.empty[String]
          val systemTags = if (docOid2SystemTags.contains(oid)) docOid2SystemTags(oid) else Seq.empty[String]
          val logicTags = DocumentApi.getLogicalTags(userTags ++ systemTags, person)
          userTags ++ logicTags
        } else {
          Seq.empty[String]
        }
      ).distinct

      val userTagsArray = allUserTags.sorted.map(ul => s""""$ul"""").mkString("[", ", ", "]")

      response.getWriter.println(userTagsArray)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      userTagsArray.length
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}

