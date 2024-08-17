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

  private def addConstraintRows(taskSheet: XSSFSheet, isTakt: Boolean, request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "addConstraintRows(): ENTRY", request)
    val cellStyle = taskSheet.getWorkbook.createCellStyle()
    cellStyle.setAlignment(HorizontalAlignment.CENTER)
    cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
    cellStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.index)
    for (n <- Seq(1, 3)) {
      val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
      Seq.range(0, 3).foreach(row.createCell(_).setCellValue("--"))
      row.createCell(3).setCellValue(2 + 3 * n)
      row.createCell(4).setCellValue(5 * (n - 2))
      row.createCell(5).setCellValue(if (isTakt) "H" else "--")
      row.setRowStyle(cellStyle)
    }
    BWLogger.log(getClass.getName, request.getMethod, "addConstraintRows(): EXIT", request)
  }

  private def addDeliverableRows(taskSheet: XSSFSheet, activity: DynDoc, request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "addDeliverableRows(): ENTRY", request)
    val cellStyle = taskSheet.getWorkbook.createCellStyle()
    cellStyle.setAlignment(HorizontalAlignment.CENTER)
    cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
    cellStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.index)
    val existingDeliverables = DeliverableApi.deliverablesByActivityOids(Seq(activity._id[ObjectId]))
    val sampleDeliverables: Seq[DynDoc] = Seq(
      Map("name" -> "WorkDeliverable", "deliverable_type" -> "Work", "duration" -> 10),
      Map("name" -> "DocumentDeliverable", "deliverable_type" -> "Document", "duration" -> 5)
    )
    for (deliverable <- existingDeliverables ++ sampleDeliverables) {
      val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
      Seq.range(0, 6).foreach(row.createCell(_).setCellValue("--"))
      row.createCell(6).setCellValue(deliverable.name[String])
      row.createCell(7).setCellValue(deliverable.deliverable_type[String])
      row.createCell(8).setCellValue(deliverable.duration[Int])
      row.setRowStyle(cellStyle)
      addConstraintRows(taskSheet, activity.is_takt[Boolean], request)
    }
    BWLogger.log(getClass.getName, request.getMethod, "addDeliverableRows(): EXIT", request)
  }

  private def addTasksSheet(workbook: XSSFWorkbook, activities: Seq[DynDoc], request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "addTasksSheet(): ENTRY", request)
    val taskSheet = workbook.createSheet("Tasks")
    val headerInfo = Seq(("Activity-Name", 60), ("A-ID", 40), ("Takt?", 15), ("Constraint", 20), ("C-Delay", 20),
        ("C-Hor/Vert", 20), ("Deliverable-Name", 60), ("D-Type", 20), ("D-Duration", 20))
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
      val taktIndicator = if (activity.getOrElse[Boolean]("is_takt", false)) {
        s"Yes (${activity.getOrElse[Int]("takt_unit_no", 1)})"
      } else {
        "No"
      }
      row.createCell(2).setCellValue(taktIndicator)
      row.setRowStyle(cellStyle)
      addDeliverableRows(taskSheet, activity, request)
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
        reportFatalException(t, getClass.getName, request, response)
    }
  }
}
