package com.buildwhiz.baf2

import com.buildwhiz.dot.GetDocumentVersionsList
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class TaskDocumentInfo extends HttpServlet with HttpUtils with DateTimeUtils {

  private def requiredDocumentItems(request: HttpServletRequest): Seq[Document] = {
    (1 to 7).map(n => {
      val docType = Seq("pdf", "excel")(n % 2)
      val count = Seq(0, 1)(n % 2)
      val itemNumber = n.toString * 7
      val date = s"2019-01-2$n"
      new Document("name", s"Req-Doc-$itemNumber").append("type", docType).append("version_date", date).
        append("version_count", count)
    })
  }

  private def additionalDocumentItems(request: HttpServletRequest): Seq[Document] = {
    (1 to 2).map(n => {
      val docType = Seq("word", "excel")(n % 2)
      val count = Seq(0, 1)(n % 2)
      val itemNumber = n.toString * 9
      val date = s"2019-02-2$n"
      new Document("name", s"Addl-Doc-$itemNumber").append("type", docType).append("version_date", date).
        append("version_count", count)
    })
  }

  private def checkListItems(request: HttpServletRequest): Seq[Document] = {
    (1 to 5).map(n => {
      val complete = (n % 2) == 0
      val itemNumber = n.toString * 5
      new Document("name", s"Check-$itemNumber").append("complete", s"$complete")
    })
  }

  private def taskDocumentRecord(request: HttpServletRequest): String = {
    val checkList = checkListItems(request: HttpServletRequest).map(bson2json).mkString("[", ", ", "]")
    val requiredDocuments = requiredDocumentItems(request: HttpServletRequest).map(bson2json).mkString("[", ", ", "]")
    val additionalDocuments = additionalDocumentItems(request: HttpServletRequest).
        map(bson2json).mkString("[", ", ", "]")
    val record = new Document("taskSpecUrl", "???").append("checkList", checkList).
        append("requiredDocuments", requiredDocuments).
        append("additionalDocuments", additionalDocuments)
    bson2json(record)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      //val activityOid = new ObjectId(parameters("activity_id"))
      //val user: DynDoc = getUser(request)
      //val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      response.getWriter.print(taskDocumentRecord(request))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}