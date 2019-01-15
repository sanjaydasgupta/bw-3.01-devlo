package com.buildwhiz.baf

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId
import com.buildwhiz.dot.GetDocumentsSummary
import org.bson.Document

class DocumentGroupFilterLabelsFetch extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val (userLabelCount, systemLabelCount) = labelsFetch(request, response)
    BWLogger.log(getClass.getName, request.getMethod,
      s"EXIT-OK (userLabelCount: $userLabelCount, systemLabelCount: $systemLabelCount)", request)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val (userLabelCount, systemLabelCount) = labelsFetch(request, response)
    BWLogger.log(getClass.getName, request.getMethod,
      s"EXIT-OK (userLabelCount: $userLabelCount, systemLabelCount: $systemLabelCount)", request)
  }

  def labelsFetch(request: HttpServletRequest, response: HttpServletResponse): (Int, Int) = {
    val parameters = getParameterMap(request)
    try {
      val user: DynDoc = getUser(request)
      val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head

      val docIds: Seq[String] = if (request.getMethod == "GET") {
        parameters("document_ids").split(",")
      } else {
        val postData: DynDoc = Document.parse(getStreamData(request))
        val ids: Seq[String] = postData.document_ids[Many[String]]
        ids
      }

      val docOids = docIds.map(id => new ObjectId(id.trim))

      val documentRecords: Seq[DynDoc] = docOids.
        map(oid => BWMongoDB3.document_master.find(Map("_id" -> oid)).head)

      val docOid2UserLabels: Map[ObjectId, Seq[String]] = GetDocumentsSummary.docOid2UserLabels(person)
      val docOid2SystemLabels: Map[ObjectId, Seq[String]] = documentRecords.
          map(docRec => (docRec._id[ObjectId], GetDocumentsSummary.getSystemLabels(docRec))).toMap

      val allUserLabels: Seq[String] = docOids.flatMap(oid =>
        if (docOid2UserLabels.contains(oid) || docOid2SystemLabels.contains(oid)) {
          val userLabels = if (docOid2UserLabels.contains(oid)) docOid2UserLabels(oid) else Seq.empty[String]
          val systemLabels = if (docOid2SystemLabels.contains(oid)) docOid2SystemLabels(oid) else Seq.empty[String]
          val logicLabels = GetDocumentsSummary.getLogicalLabels(userLabels ++ systemLabels, person)
          //val labelsMessage = s"userLabels: ${userLabels.mkString("[", ",", "]")}," +
          //    s"systemLabels: ${systemLabels.mkString("[", ",", "]")})" +
          //    s"logicLabels: ${logicLabels.mkString("[", ",", "]")})"
          //BWLogger.log(getClass.getName, "labelsFetch", labelsMessage, request)
          userLabels ++ logicLabels
        } else {
          Seq.empty[String]
        }
      ).distinct

      val csvUserLabels = allUserLabels.sorted.map(ul => s"""{"name": "$ul"}""").mkString("[", ", ", "]")

      val systemLabels: Seq[String] = documentRecords.
        flatMap(docRecord => GetDocumentsSummary.getSystemLabels(docRecord)).distinct.sorted
      val csvSystemLabels = systemLabels.map(ul => s"""{"name": "$ul"}""").mkString("[", ", ", "]")

      response.getWriter.println(s"""{"user": $csvUserLabels, "system": $csvSystemLabels}""")
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      (csvUserLabels.length, csvSystemLabels.length)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}

