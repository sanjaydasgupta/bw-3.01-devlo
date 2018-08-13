package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.xssf.usermodel.{XSSFSheet, XSSFWorkbook}
import org.bson.types.ObjectId
import org.bson.Document

class PhaseConfigDownload extends HttpServlet with HttpUtils {

  private def makeHeaderRow(taskSheet: XSSFSheet, headerInfo: Seq[(String, Int)]): Unit = {
    val cellStyle = taskSheet.getWorkbook.createCellStyle()
    cellStyle.setAlignment(HorizontalAlignment.CENTER)
    val cellFont = cellStyle.getFont
    cellFont.setBold(true)
    cellStyle.setFont(cellFont)
    cellStyle.setLocked(true)
    val headerRow = taskSheet.createRow(0)
    headerRow.setRowStyle(cellStyle)
    for (hdrInfo <- headerInfo.zipWithIndex) {
      val cell = headerRow.createCell(hdrInfo._2)
      cell.setCellValue(hdrInfo._1._1)
      cell.getSheet.setColumnWidth(hdrInfo._2, hdrInfo._1._2 * 125)
    }
  }

  private def addTasksSheet(workbook: XSSFWorkbook, project: DynDoc, phase: DynDoc, bpmnName: String): Int = {
    val activityIds: Seq[ObjectId] = phase.activity_ids[Many[ObjectId]]
    val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityIds),
      "bpmn_name" -> bpmnName))
    val taskSheet = workbook.createSheet("Tasks")
    val headerInfo = Seq(("Task-Name", 60), ("Type", 20), ("Parent", 60), ("Duration", 20), ("Assignee", 50), ("Description", 100))
    makeHeaderRow(taskSheet, headerInfo)
    for (activity <- activities) {
      val actions: Seq[DynDoc] = activity.actions[Many[Document]]
      val actionRowPairs = actions.map(a => (a, taskSheet.createRow(taskSheet.getLastRowNum + 1)))
      for ((task, row) <- actionRowPairs) {
        val taskName = task.name[String]
        row.createCell(0).setCellValue(s"$taskName")
        val aType = task.`type`[String]
        row.createCell(1).setCellValue(aType)
        // parent's _id ...
        row.createCell(2).setCellValue(activity._id[ObjectId].toString)
        val duration = task.duration[String]
        row.createCell(3).setCellValue(duration)
        val assignee = task.assignee_person_id[ObjectId]
        row.createCell(4).setCellValue(assignee.toString)
        val description = activity.description[String]
        row.createCell(5).setCellValue(description)
      }
    }
    taskSheet.getLastRowNum + 1
  }

  private def addVariablesSheet(workbook: XSSFWorkbook, project: DynDoc, phase: DynDoc, bpmnName: String): Int = {
    val variablesSheet = workbook.createSheet("Variables")
    val headerInfo = Seq(("Variable-Name", 60), ("Type", 20), ("Value", 20))
    makeHeaderRow(variablesSheet, headerInfo)
    val variables: Seq[DynDoc] = phase.variables[Many[Document]].filter(_.bpmn_name[String] == bpmnName)
    for (variable <- variables) {
      val row = variablesSheet.createRow(variablesSheet.getLastRowNum + 1)
      val variableName = variable.label[String]
      row.createCell(0).setCellValue(s"$variableName")
      val vType = variable.`type`[String]
      row.createCell(1).setCellValue(s"$vType")
      val value = variable.value[AnyRef].toString
      row.createCell(2).setCellValue(s"$value")
      //val buffer = s"VARIABLE, $variableName ($projectName/$phaseName), $vType, $value"
    }
    variablesSheet.getLastRowNum + 1
  }

  private def addTimersSheet(workbook: XSSFWorkbook, project: DynDoc, phase: DynDoc, bpmnName: String): Int = {
    val timersSheet = workbook.createSheet("Timers")
    val headerInfo = Seq(("Timer-Name", 60), ("Type", 20), ("Value", 20))
    makeHeaderRow(timersSheet, headerInfo)
    val timers: Seq[DynDoc] = phase.timers[Many[Document]].filter(_.bpmn_name[String] == bpmnName)
    for (timer <- timers) {
      val row = timersSheet.createRow(timersSheet.getLastRowNum + 1)
      val timerName = timer.name[String]
      row.createCell(0).setCellValue(s"$timerName")
      row.createCell(1).setCellValue("DURATION")
      row.createCell(2).setCellValue(timer.duration[String])
    }
    timersSheet.getLastRowNum + 1
  }

  private def addDocumentsSheet(workbook: XSSFWorkbook, project: DynDoc, phase: DynDoc, bpmnName: String): Int = {
    val documentSheet = workbook.createSheet("Documents")
    val headerInfo = Seq(("Doc Name", 40), ("Doc Descr", 40), ("Labels", 40),
      ("Mandatory", 25), ("Content Type", 25), ("Activity", 40), ("Task", 40), ("Doc ID", 40))
    makeHeaderRow(documentSheet, headerInfo)
    val activityIds: Seq[ObjectId] = phase.activity_ids[Many[ObjectId]]
    val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityIds),
      "bpmn_name" -> bpmnName))
    for (activity <- activities) {
      val actions: Seq[DynDoc] = activity.actions[Many[Document]]
      for (task <- actions) {
        val existingTaskDocs: Seq[DynDoc] = BWMongoDB3.document_master.
          find(Map("activity_id" -> activity._id[ObjectId], "action_name" -> task.name[String]))
        for (doc <- existingTaskDocs) {
          val row = documentSheet.createRow(documentSheet.getLastRowNum + 1)
          row.createCell(0).setCellValue(doc.name[String])
          row.createCell(1).setCellValue(doc.description[String])
          row.createCell(2).setCellValue(doc.labels[Many[String]].mkString(","))
          row.createCell(3).setCellValue(if (doc.mandatory[Boolean]) "yes" else "no")
          row.createCell(4).setCellValue(doc.content_type[String])
          // owner task reference ...
          row.createCell(5).setCellValue(doc.activity_id[ObjectId].toString)
          row.createCell(6).setCellValue(doc.action_name[String])
          // document _id ...
          row.createCell(7).setCellValue(doc._id[ObjectId].toString)
        }
        if (existingTaskDocs.isEmpty) {
          val row = documentSheet.createRow(documentSheet.getLastRowNum + 1)
          row.createCell(0).setCellValue("name")
          row.createCell(1).setCellValue("description")
          row.createCell(2).setCellValue("comma-separated labels")
          row.createCell(3).setCellValue("yes/no")
          row.createCell(4).setCellValue("image/pdf/word/excel/bim/xml")
          // owner task reference ...
          row.createCell(5).setCellValue(activity._id[ObjectId].toString)
          row.createCell(6).setCellValue(task.name[String])
          // document _id ...
          row.createCell(7).setCellValue("-")
        }
      }
    }
    documentSheet.getLastRowNum + 1
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val phaseOid = new ObjectId(parameters("phase_id"))
      val bpmnName = parameters("bpmn_name")
      val workbook = new XSSFWorkbook()
      val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val taskNbr = addTasksSheet(workbook, project, phase, bpmnName)
      val varNbr = addVariablesSheet(workbook, project, phase, bpmnName)
      val timerNbr = addTimersSheet(workbook, project, phase, bpmnName)
      val docNbr = addDocumentsSheet(workbook, project, phase, bpmnName)
      workbook.write(response.getOutputStream)
      response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet",
        s"EXIT-OK ($taskNbr tasks, $varNbr variables, $timerNbr timers, $docNbr documents)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
