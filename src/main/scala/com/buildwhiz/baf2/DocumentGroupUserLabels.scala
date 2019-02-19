package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class DocumentGroupUserLabels extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val userLabelCount = labelsFetch(request, response)
    BWLogger.log(getClass.getName, request.getMethod,
      s"EXIT-OK ($userLabelCount)", request)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val userLabelCount = labelsFetch(request, response)
    BWLogger.log(getClass.getName, request.getMethod,
      s"EXIT-OK ($userLabelCount)", request)
  }

  def labelsFetch(request: HttpServletRequest, response: HttpServletResponse): Int = {
    val parameters = getParameterMap(request)
    try {
      val user: DynDoc = getUser(request)
      val person: DynDoc = PersonApi.personById(user._id[ObjectId])

      val docIds: Seq[String] = if (request.getMethod == "GET") {
        parameters("document_ids").split(",")
      } else {
        val postData: DynDoc = Document.parse(getStreamData(request))
        val ids: Seq[String] = postData.document_ids[Many[String]]
        ids
      }

      val docOids: Seq[ObjectId] = docIds.map(id => new ObjectId(id.trim))

      val documentRecords: Seq[DynDoc] = BWMongoDB3.document_master.find(Map("_id" -> Map("$in" -> docOids)))

      val docOid2UserLabels: Map[ObjectId, Seq[String]] = DocumentApi.docOid2UserTags(person)
      val docOid2SystemLabels: Map[ObjectId, Seq[String]] = documentRecords.
          map(docRec => (docRec._id[ObjectId], DocumentApi.getSystemTags(docRec))).toMap

      val allUserLabels: Seq[String] = docOids.flatMap(oid =>
        if (docOid2UserLabels.contains(oid) || docOid2SystemLabels.contains(oid)) {
          val userLabels = if (docOid2UserLabels.contains(oid)) docOid2UserLabels(oid) else Seq.empty[String]
          val systemLabels = if (docOid2SystemLabels.contains(oid)) docOid2SystemLabels(oid) else Seq.empty[String]
          val logicLabels = DocumentApi.getLogicalTags(userLabels ++ systemLabels, person)
          userLabels ++ logicLabels
        } else {
          Seq.empty[String]
        }
      ).distinct

      val csvUserLabels = allUserLabels.sorted.map(ul => s""""$ul"""").mkString("[", ", ", "]")

      response.getWriter.println(csvUserLabels)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      csvUserLabels.length
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}

