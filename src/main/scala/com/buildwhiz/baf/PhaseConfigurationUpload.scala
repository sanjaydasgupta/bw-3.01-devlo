package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import org.apache.poi.ss.usermodel.{Row, Sheet}
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class PhaseConfigurationUpload extends HttpServlet with HttpUtils with MailUtils {

  private def processTasks(request: HttpServletRequest, response: HttpServletResponse, taskSheet: Sheet,
                           bpmnName: String): String = {

    def setTask(name: String, description: String, typ: String, activityId: String, duration: String,
                assignee: String): Unit = {
      val activityOid = new ObjectId(activityId)
      val theActivity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).head
      val actions: Seq[DynDoc] = theActivity.actions[Many[Document]]
      actions.find(_.name[String] == name) match {
        case None =>
          ActionAdd.add(request, activityOid, name, description, typ, bpmnName, new ObjectId(assignee), duration)
        case Some(action) =>
          if (action.status[String] != "defined") {
            throw new IllegalArgumentException(s"wrong status: '${action.status[String]}'")
          }
          if (typ == "#")
            ActionDelete.delete(request, activityOid, name)
          else
            ActionDurationSet.set(request, activityOid, name, duration, Some(description))
      }
    }

    BWLogger.log(getClass.getName, "processTasks", "ENTRY", request)
    val rows: Iterator[Row] = taskSheet.rowIterator.asScala
    val header = rows.take(1).toSeq.head
    val cellCount = header.getPhysicalNumberOfCells
    if (cellCount != 6)
      throw new IllegalArgumentException(s"unexpected cell count ($cellCount) in header row}")
    for (row <- rows) {
      row.cellIterator.asScala.toSeq.map(_.getStringCellValue) match {
        case Seq(name, typ, activityId, duration, assignee, description) =>
          setTask(name, description, typ, activityId, duration, assignee)
        case _ => throw new IllegalArgumentException(s"unexpected cell count ($cellCount) in row ${header.getRowNum + 1}")
      }
    }
    s"${taskSheet.getPhysicalNumberOfRows -1} task(s)"
  }

  private def processDocuments(request: HttpServletRequest, response: HttpServletResponse, documentSheet: Sheet,
                               phaseOid: ObjectId, bpmnName: String): String = {

    def setDocument(docName: String, docDescription: String, labels: String, mandatory: Boolean,
                    contentType: String, activityId: String, taskName: String, documentId: String): Unit = {
      val activityOid = new ObjectId(activityId)
      val theActivity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).head
      val actions: Seq[DynDoc] = theActivity.actions[Many[Document]]
      val project: DynDoc = BWMongoDB3.projects.find(Map("phase_ids" -> phaseOid)).head
      actions.find(_.name[String] == taskName) match {
        case None => throw new IllegalArgumentException(s"unknown task: '$taskName'")
        case Some(action) =>
          if (action.status[String] != "defined") {
            throw new IllegalArgumentException(s"wrong status: '${action.status[String]}'")
          }
      }
      val documentOid = Try { new ObjectId(documentId) }
      val docRecordExists = documentOid match {
        case Failure(_) => false
        case Success(oid) => BWMongoDB3.document_master.count(Map("_id" -> oid)) > 0
      }
      val labelSequence = labels.split(",").map(_.trim).toSeq
      if (docRecordExists) {
        if (contentType == "#") {
          BWMongoDB3.document_master.deleteOne(Map("_id" -> documentOid.get))
        } else {
          val updateResult = BWMongoDB3.document_master.updateOne(Map("_id" -> documentOid.get),
            Map("$set" -> Map("name" -> docName, "description" -> docDescription, "labels" -> labelSequence,
              "mandatory" -> mandatory, "content_type" -> contentType, "activity_id" -> activityOid,
              "action_name" -> taskName)))
          if (updateResult.getMatchedCount == 0)
            throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
        }
      } else {
        BWMongoDB3.document_master.insertOne(Map("project_id" -> project._id[ObjectId],
          "activity_id" -> activityOid, "action_name" -> taskName, "name" -> docName,
          "description" -> docDescription, "labels" -> labelSequence, "versions" -> Seq.empty[Document],
          "content_type" -> contentType, "mandatory" -> mandatory))
      }
    }

    BWLogger.log(getClass.getName, "processDocuments", "ENTRY", request)
    val rows: Iterator[Row] = documentSheet.rowIterator.asScala
    val header = rows.take(1).toSeq.head
    val cellCount = header.getPhysicalNumberOfCells
    if (cellCount != 8)
      throw new IllegalArgumentException(s"unexpected cell count ($cellCount) in header row}")
    for (row <- rows) {
      row.cellIterator.asScala.toSeq.map(_.getStringCellValue) match {
        case Seq(docName, docDescription, labels, mandatory, contentType, activityId, taskName, documentId) =>
          setDocument(docName, docDescription, labels, mandatory.toLowerCase == "yes", contentType, activityId, taskName, documentId)
        case _ => throw new IllegalArgumentException(s"unexpected cell count ($cellCount) in row ${header.getRowNum + 1}")
      }
    }
    s"${documentSheet.getPhysicalNumberOfRows - 1} variable(s)"
  }

  private def processVariables(request: HttpServletRequest, response: HttpServletResponse, variableSheet: Sheet,
                               phaseOid: ObjectId, bpmnName: String): String = {

    def setVariable(name: String, typ: String, value: String): Unit = {
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val variables: Option[DynDoc] = phase.variables[Many[Document]].
        find(v => v.bpmn_name[String] == bpmnName && v.label[String] == name)
      if (variables.isEmpty)
        throw new IllegalArgumentException(s"no such variable: $name")
      VariableValueSet.set(request, response, phaseOid, name, bpmnName, value)
    }

    BWLogger.log(getClass.getName, "processVariables", "ENTRY", request)
    val rows: Iterator[Row] = variableSheet.rowIterator.asScala
    val header = rows.take(1).toSeq.head
    val cellCount = header.getPhysicalNumberOfCells
    if (cellCount != 3)
      throw new IllegalArgumentException(s"unexpected cell count ($cellCount) in header row")
    for (row <- rows) {
      row.cellIterator.asScala.toSeq match {
        case Seq(name, typ, value) => setVariable(name.getStringCellValue, typ.getStringCellValue, value.getStringCellValue)
        case _ => throw new IllegalArgumentException(s"unexpected cell count ($cellCount) in row ${header.getRowNum + 1}")
      }
    }
    s"${variableSheet.getPhysicalNumberOfRows - 1} variable(s)"
  }

  private def processTimers(request: HttpServletRequest, response: HttpServletResponse, timersSheet: Sheet,
                            phaseOid: ObjectId, bpmnName: String): String = {

    def setTimer(name: String, typ: String, duration: String): Unit = {
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val timers: Option[DynDoc] = phase.timers[Many[Document]].
        find(t => t.bpmn_name[String] == bpmnName && t.name[String] == name)
      if (timers.isEmpty)
        throw new IllegalArgumentException(s"no such timer: $name")
      TimerDurationSet.set(request, phaseOid, None, Some(name), bpmnName, duration)
    }

    BWLogger.log(getClass.getName, "processTimers", "ENTRY", request)
    val rows: Iterator[Row] = timersSheet.rowIterator.asScala
    val header = rows.take(1).toSeq.head
    val cellCount = header.getPhysicalNumberOfCells
    if (cellCount != 3)
      throw new IllegalArgumentException(s"unexpected cell count ($cellCount) in header row")
    for (row <- rows) {
      row.cellIterator.asScala.toSeq match {
        case Seq(name, typ, duration) => setTimer(name.getStringCellValue, typ.getStringCellValue, duration.getStringCellValue)
        case _ => throw new IllegalArgumentException(s"unexpected cell count ($cellCount) in row ${header.getRowNum + 1}")
      }
    }
    s"${timersSheet.getPhysicalNumberOfRows - 1} timer(s)"
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
      val bpmnName = parameters("bpmn_name")
      val parts = request.getParts
      if (parts.size != 1)
        throw new IllegalArgumentException(s"Unexpected number of files ${parts.size}")
      val workbook = new XSSFWorkbook(parts.asScala.head.getInputStream)
      val nbrOfSheets = workbook.getNumberOfSheets
      if (nbrOfSheets != 4)
        throw new IllegalArgumentException(s"unexpected number of sheets: $nbrOfSheets")
      val sheets: Iterator[Sheet] = workbook.sheetIterator.asScala
      val itemsUploaded: String = sheets.map(s => (s.getSheetName, s)).map({
        case ("Tasks", sheet) => processTasks(request, response, sheet, bpmnName)
        case ("Variables", sheet) => processVariables(request, response, sheet, phaseOid, bpmnName)
        case ("Timers", sheet) => processTimers(request, response, sheet, phaseOid, bpmnName)
        case ("Documents", sheet) => processDocuments(request, response, sheet, phaseOid, bpmnName)
        case (other, _) => throw new IllegalArgumentException(s"unexpected sheet name: $other")
      }).mkString(", ")
      BWLogger.audit(getClass.getName, "doPost", s"Uploaded phase configuration Excel ($nbrOfSheets sheets: $itemsUploaded)", request)
      response.getWriter.print(s"""{"nbrOfSheets": "$nbrOfSheets", "items": "$itemsUploaded"}""")
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
