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

  private def requiredDocumentItems(user: DynDoc, activity: DynDoc, process: DynDoc, phase: DynDoc, project: DynDoc):
      Seq[Document] = {
    (1 to 7).map(n => {
      val docType = Seq("pdf", "excel")(n % 2)
      val count = Seq(0, 1)(n % 2)
      val itemNumber = n.toString * 7
      val date = s"2019-01-2$n"
      new Document("name", s"Req-Doc-$itemNumber").append("type", docType).append("version_date", date).
        append("version_count", count)
    })
  }

  private def additionalDocumentItems(user: DynDoc, activity: DynDoc, process: DynDoc, phase: DynDoc, project: DynDoc):
      Seq[Document] = {
    (1 to 2).map(n => {
      val docType = Seq("word", "excel")(n % 2)
      val count = Seq(0, 1)(n % 2)
      val itemNumber = n.toString * 9
      val date = s"2019-02-2$n"
      new Document("name", s"Addl-Doc-$itemNumber").append("type", docType).append("version_date", date).
        append("version_count", count)
    })
  }

  private def checkListItems(user: DynDoc, activity: DynDoc, process: DynDoc, phase: DynDoc, project: DynDoc):
      Seq[Document] = {
    (1 to 5).map(n => {
      val complete = (n % 2) == 0
      val itemNumber = n.toString * 5
      new Document("name", s"Check-$itemNumber").append("complete", s"$complete")
    })
  }

  private def taskDocumentRecord(user: DynDoc, activity: DynDoc, process: DynDoc, phase: DynDoc, project: DynDoc):
      String = {
    val checkList = checkListItems(user, activity, process, phase, project)
    val requiredDocuments = requiredDocumentItems(user, activity, process, phase, project)
    val additionalDocuments = additionalDocumentItems(user, activity, process, phase, project)
    val enableAddButtons = PersonApi.isBuildWhizAdmin(user._id[ObjectId]) ||
        ProcessApi.canManage(user._id[ObjectId], process)
    val record = new Document("task_specification_url", "???").append("check_list", checkList).
        append("required_documents", requiredDocuments).append("additional_documents", additionalDocuments).
        append("enable_add_buttons", enableAddButtons)
    bson2json(record)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val activityOid = new ObjectId(parameters("activity_id"))
      val theActivity = ActivityApi.activityById(activityOid)
      val parentProcess = ActivityApi.parentProcess(activityOid)
      val ancestorPhase = ProcessApi.parentPhase(parentProcess._id[ObjectId])
      val ancestorProject = PhaseApi.parentProject(ancestorPhase._id[ObjectId])
      val user: DynDoc = getUser(request)
      val freshUserRecord = PersonApi.personById(user._id[ObjectId])
      response.getWriter.print(taskDocumentRecord(freshUserRecord, theActivity, parentProcess, ancestorPhase, ancestorProject))
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