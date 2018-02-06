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
    val projectName = project.name[String]
    val phaseName = phase.name[String]
    for (activity <- activities) {
      val actions: Seq[DynDoc] = activity.actions[Many[Document]]
      val actionRowPairs = actions.map(a => (a, taskSheet.createRow(taskSheet.getLastRowNum + 1)))
      val parentRow = actionRowPairs.find(_._1.`type`[String] == "main").get._2
      for ((task, row) <- actionRowPairs) {
        val taskName = task.name[String]
        row.createCell(0).setCellValue(s"$taskName ($projectName/$phaseName)")
        val aType = task.`type`[String]
        row.createCell(1).setCellValue(aType)
        row.createCell(2).setCellFormula(s"A${parentRow.getRowNum + 1}")
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
    val projectName = project.name[String]
    val phaseName = phase.name[String]
    val variables: Seq[DynDoc] = phase.variables[Many[Document]].filter(_.bpmn_name[String] == bpmnName)
    for (variable <- variables) {
      val row = variablesSheet.createRow(variablesSheet.getLastRowNum + 1)
      val variableName = variable.label[String]
      row.createCell(0).setCellValue(s"$variableName ($projectName/$phaseName)")
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
    val projectName = project.name[String]
    val phaseName = phase.name[String]
    val timers: Seq[DynDoc] = phase.timers[Many[Document]].filter(_.bpmn_name[String] == bpmnName)
    for (timer <- timers) {
      val row = timersSheet.createRow(timersSheet.getLastRowNum + 1)
      val timerName = timer.name[String]
      row.createCell(0).setCellValue(s"$timerName ($projectName/$phaseName)")
      row.createCell(1).setCellValue("DURATION")
      val duration = timer.duration[String]
      row.createCell(2).setCellValue(duration)
    }
    timersSheet.getLastRowNum + 1
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
      val taskCount = addTasksSheet(workbook, project, phase, bpmnName)
      val variableCount = addVariablesSheet(workbook, project, phase, bpmnName)
      val timerCount = addTimersSheet(workbook, project, phase, bpmnName)
      workbook.write(response.getOutputStream)
      response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet",
        s"EXIT-OK ($taskCount tasks, $variableCount variables, $timerCount timers)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
