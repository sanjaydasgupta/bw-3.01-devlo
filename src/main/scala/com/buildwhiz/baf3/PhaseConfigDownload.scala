package com.buildwhiz.baf3

import com.buildwhiz.baf2.{PersonApi, PhaseApi}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.apache.poi.ss.usermodel.{FillPatternType, HorizontalAlignment, IndexedColors, VerticalAlignment}
import org.apache.poi.xssf.usermodel.{XSSFColor, XSSFSheet, XSSFWorkbook}
import org.bson.Document
import org.bson.types.ObjectId
import org.openxmlformats.schemas.spreadsheetml.x2006.main.{CTCol, CTColor}

import scala.jdk.CollectionConverters._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class PhaseConfigDownload extends HttpServlet with HttpUtils {

  private def makeHeaderRow(taskSheet: XSSFSheet, headerInfo: Seq[(String, Int)]): Unit = {
    val cellStyle = taskSheet.getWorkbook.createCellStyle()
    cellStyle.setAlignment(HorizontalAlignment.CENTER)
    val cellFont = cellStyle.getFont
    cellFont.setBold(true)
    cellStyle.setFont(cellFont)
    cellStyle.setLocked(true)
    cellStyle.setVerticalAlignment(VerticalAlignment.CENTER)
    val headerRow = taskSheet.createRow(0)
    headerRow.setRowStyle(cellStyle)
    val rowHeight = if (headerInfo.exists(_._1.contains("\n"))) 36 else 18
    headerRow.setHeightInPoints(rowHeight.toFloat)
    for (hdrInfo <- headerInfo.zipWithIndex) {
      val cell = headerRow.createCell(hdrInfo._2)
      cell.setCellValue(hdrInfo._1._1)
      cell.getSheet.setColumnWidth(hdrInfo._2, hdrInfo._1._2 * 125)
    }
  }

  private def addConstraintRows(taskSheet: XSSFSheet, deliverableOid: ObjectId, request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "addConstraintRows(): ENTRY", request)
    val cellStyle = taskSheet.getWorkbook.createCellStyle()
    cellStyle.setAlignment(HorizontalAlignment.CENTER)
    cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
    cellStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.index)
    for (n <- Seq(1, 3)) {
      val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
      Seq.range(0, 6).foreach(row.createCell(_).setCellValue("--"))
      row.createCell(6).setCellValue(2 + 3 * n)
      row.createCell(7).setCellValue(5 * (n - 2))
      row.setRowStyle(cellStyle)
    }
    BWLogger.log(getClass.getName, request.getMethod, "addConstraintRows(): EXIT", request)
  }

  private def addDeliverableRows(taskSheet: XSSFSheet, activityOid: ObjectId, request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "addDeliverableRows(): ENTRY", request)
    val cellStyle = taskSheet.getWorkbook.createCellStyle()
    cellStyle.setAlignment(HorizontalAlignment.CENTER)
    cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
    cellStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.index)
    val deliverables = DeliverableApi.deliverablesByActivityOids(Seq(activityOid))
    for (deliverable <- deliverables) {
      val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
      Seq.range(0, 3).foreach(row.createCell(_).setCellValue("--"))
      row.createCell(3).setCellValue(deliverable.name[String])
      row.createCell(4).setCellValue(deliverable.deliverable_type[String])
      row.createCell(5).setCellValue(deliverable.duration[Int])
      row.setRowStyle(cellStyle)
      addConstraintRows(taskSheet, deliverable._id[ObjectId], request)
    }
    BWLogger.log(getClass.getName, request.getMethod, "addDeliverableRows(): EXIT", request)
  }

  private def addTasksSheet(workbook: XSSFWorkbook, activities: Seq[DynDoc], request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "addTasksSheet(): ENTRY", request)
    val taskSheet = workbook.createSheet("Tasks")
    val headerInfo = Seq(("Activity-Name", 60), ("A-ID", 40), ("Takt#", 20), ("Deliverable-Name", 60), ("D-Type", 20),
      ("D-Duration", 20), ("Const-Row#", 20), ("Const-Delay", 20))
    makeHeaderRow(taskSheet, headerInfo)
    val cellStyle = taskSheet.getWorkbook.createCellStyle()
    cellStyle.setAlignment(HorizontalAlignment.CENTER)
    cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
    cellStyle.setFillForegroundColor(IndexedColors.BLUE.index)
    for (activity <- activities) {
      val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
      val activityName = activity.get[String]("full_path_name") match {
        case Some(fpn) => fpn.substring(2)
        case None => activity.name[String]
      }
      val activityOid = activity._id[ObjectId]
      row.createCell(0).setCellValue(activityName)
      row.createCell(1).setCellValue(activityOid.toString)
      row.createCell(2).setCellValue(activity.getOrElse[Int]("takt_unit_no", 1))
      row.setRowStyle(cellStyle)
      addDeliverableRows(taskSheet, activityOid, request)
    }
    BWLogger.log(getClass.getName, request.getMethod, "addTasksSheet(): ENTRY", request)
  }

  private def addVariablesSheet(workbook: XSSFWorkbook, process: DynDoc, bpmnName: String): Unit = {
    val variablesSheet = workbook.createSheet("Variables")
    val headerInfo = Seq(("Variable-Name", 60), ("Type", 20), ("Value", 20))
    makeHeaderRow(variablesSheet, headerInfo)
    val variables: Seq[DynDoc] = process.variables[Many[Document]].filter(_.bpmn_name[String] == bpmnName)
    for (variable <- variables) {
      val row = variablesSheet.createRow(variablesSheet.getLastRowNum + 1)
      val variableName = variable.label[String]
      row.createCell(0).setCellValue(s"$variableName")
      val vType = variable.`type`[String]
      row.createCell(1).setCellValue(s"$vType")
      val value = variable.value[AnyRef].toString
      row.createCell(2).setCellValue(s"$value")
      //val buffer = s"VARIABLE, $variableName ($projectName/$processName), $vType, $value"
    }
  }

  private def addTimersSheet(workbook: XSSFWorkbook, process: DynDoc, bpmnName: String): Unit = {
    val timersSheet = workbook.createSheet("Timers")
    val headerInfo = Seq(("Timer-Name", 60), ("Type", 20), ("Value", 20))
    makeHeaderRow(timersSheet, headerInfo)
    val timers: Seq[DynDoc] = process.timers[Many[Document]].filter(_.bpmn_name[String] == bpmnName)
    for (timer <- timers) {
      val row = timersSheet.createRow(timersSheet.getLastRowNum + 1)
      val timerName = timer.name[String]
      row.createCell(0).setCellValue(s"$timerName")
      row.createCell(1).setCellValue("DURATION")
      row.createCell(2).setCellValue(timer.duration[String])
    }
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
      val phase = PhaseApi.phaseById(phaseOid)
      val activities = PhaseApi.allActivities(Right(phase))
      BWLogger.log(getClass.getName, request.getMethod, s"Activity count: ${activities.length}", request)
      val workbook = new XSSFWorkbook()
      addTasksSheet(workbook, activities, request)
      val process: DynDoc = PhaseApi.allProcesses(phase).head
      val bpmnName = process.bpmn_name[String]
      addVariablesSheet(workbook, process, bpmnName)
      addTimersSheet(workbook, process, bpmnName)
      workbook.write(response.getOutputStream)
      response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
