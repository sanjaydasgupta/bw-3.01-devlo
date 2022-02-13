package com.buildwhiz.baf3

import com.buildwhiz.baf2.{PersonApi, PhaseApi}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.apache.poi.ss.usermodel.{HorizontalAlignment, VerticalAlignment}
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

  private def addDeliverableRows(taskSheet: XSSFSheet, activityOid: ObjectId, request: HttpServletRequest): Int = {
    BWLogger.log(getClass.getName, request.getMethod, "addDeliverableRows(): ENTRY", request)
    val cellStyle = taskSheet.getWorkbook.createCellStyle()
    cellStyle.setAlignment(HorizontalAlignment.CENTER)
    val rgb: Array[Byte] = Seq(0f, 0f, 255).map(_.toByte).toArray
    cellStyle.setFillBackgroundColor(new XSSFColor(rgb))
    val deliverables = DeliverableApi.deliverablesByActivityOids(Seq(activityOid))
    for (deliverable <- deliverables) {
      val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
      row.createCell(0).setCellValue("--")
      row.createCell(1).setCellValue("--")
      row.createCell(2).setCellValue("--")
      row.createCell(3).setCellValue(deliverable.name[String])
      row.createCell(4).setCellValue(deliverable.deliverable_type[String])
      row.createCell(5).setCellValue(deliverable.duration[Int])
      row.createCell(6).setCellValue(deliverable.status[String])
      row.setRowStyle(cellStyle)
    }
    BWLogger.log(getClass.getName, request.getMethod, "addDeliverableRows(): ENTRY", request)
    deliverables.length
  }

  private def addTasksSheet(workbook: XSSFWorkbook, activities: Seq[DynDoc], request: HttpServletRequest): Int = {
    BWLogger.log(getClass.getName, request.getMethod, "addTasksSheet(): ENTRY", request)
    val taskSheet = workbook.createSheet("Tasks")
    val headerInfo = Seq(("Activity-Name", 60), ("A-ID", 20), ("A-Status", 20), ("Deliverable-Name", 60), ("D-Type", 20),
      ("D-Duration", 20), ("D-Status", 60))
    makeHeaderRow(taskSheet, headerInfo)
    val cellStyle = taskSheet.getWorkbook.createCellStyle()
    cellStyle.setAlignment(HorizontalAlignment.CENTER)
    val rgb: Array[Byte] = Seq(0, 0, 255).map(_.toByte).toArray
    cellStyle.setFillBackgroundColor(new XSSFColor(rgb))
    for (activity <- activities) {
      val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
      val activityName = activity.get[String]("full_path_name") match {
        case Some(fpn) => fpn.substring(2)
        case None => activity.name[String]
      }
      val activityOid = activity._id[ObjectId]
      row.createCell(0).setCellValue(activityName)
      row.createCell(1).setCellValue(activityOid.toString)
      row.createCell(2).setCellValue(activity.status[String])
      row.setRowStyle(cellStyle)
      addDeliverableRows(taskSheet, activityOid, request)
    }
    BWLogger.log(getClass.getName, request.getMethod, "addTasksSheet(): ENTRY", request)
    taskSheet.getLastRowNum + 1
  }

  private def addVariablesSheet(workbook: XSSFWorkbook, process: DynDoc, bpmnName: String): Int = {
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
    variablesSheet.getLastRowNum + 1
  }

  private def addTimersSheet(workbook: XSSFWorkbook, process: DynDoc, bpmnName: String): Int = {
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
    timersSheet.getLastRowNum + 1
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
      //val process: DynDoc = BWMongoDB3.processes.find(Map("_id" -> phaseOid)).head
      val taskNbr = addTasksSheet(workbook, activities, request)
      //val varNbr = addVariablesSheet(workbook, process, bpmnName)
      //val timerNbr = addTimersSheet(workbook, process, bpmnName)
      workbook.write(response.getOutputStream)
      response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod,
        s"EXIT-OK ($taskNbr tasks)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
