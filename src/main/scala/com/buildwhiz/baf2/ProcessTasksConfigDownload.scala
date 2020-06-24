package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.apache.poi.ss.usermodel.{FillPatternType, HorizontalAlignment, IndexedColors, VerticalAlignment}
import org.apache.poi.xssf.usermodel.{XSSFSheet, XSSFWorkbook}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class ProcessTasksConfigDownload extends HttpServlet with HttpUtils {

  private def makeHeaderRow(taskSheet: XSSFSheet, headerInfo: Seq[(String, Int)]): Unit = {
    val headerRow = taskSheet.createRow(0)
    //headerRow.setRowStyle(cellStyle)
    val rowHeight = if (headerInfo.exists(_._1.contains("\n"))) 36 else 18
    headerRow.setHeightInPoints(rowHeight)
    for (hdrInfo <- headerInfo.zipWithIndex) {
      val cell = headerRow.createCell(hdrInfo._2)
      cell.setCellValue(hdrInfo._1._1)
      cell.getSheet.setColumnWidth(hdrInfo._2, hdrInfo._1._2 * 125)
      val cellStyle = taskSheet.getWorkbook.createCellStyle()
      cellStyle.setAlignment(HorizontalAlignment.CENTER)
      cellStyle.setVerticalAlignment(VerticalAlignment.CENTER)
      val cellFont = cellStyle.getFont
      cellFont.setBold(true)
      cellStyle.setFont(cellFont)
      cellStyle.setFillForegroundColor(IndexedColors.CORAL.index)
      cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
      //cellStyle.setWrapText(true)
      //cellStyle.setLocked(true)
      cell.setCellStyle(cellStyle)
    }
  }

  private def addTasksSheet(workbook: XSSFWorkbook, process: DynDoc, bpmnName: String): Int = {
    def addTaskRow(taskSheet: XSSFSheet, taskName: String): Unit = {
      val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
      row.createCell(0).setCellValue(taskName)
      row.createCell(1).setCellValue("--") // deliverable
      row.createCell(2).setCellValue("--") // type
      row.createCell(3).setCellValue("--") // duration
      row.createCell(4).setCellValue("--") // constraint
      row.createCell(5).setCellValue("--") // duration
      row.createCell(6).setCellValue("--") // constraint
      val cellStyle = taskSheet.getWorkbook.createCellStyle()
      cellStyle.setAlignment(HorizontalAlignment.CENTER)
      cellStyle.setVerticalAlignment(VerticalAlignment.CENTER)
      cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
      cellStyle.setFillForegroundColor(IndexedColors.GREY_40_PERCENT.index)
      row.cellIterator().forEachRemaining(_.setCellStyle(cellStyle))
    }
    def addDeliverableRow(taskSheet: XSSFSheet, deliverables: Seq[DynDoc]): Unit = {
      def addConstraintRow(taskSheet: XSSFSheet, offset: String, duration: String): Unit = {
        val constraintCellStyle = taskSheet.getWorkbook.createCellStyle()
        constraintCellStyle.setAlignment(HorizontalAlignment.CENTER)
        constraintCellStyle.setVerticalAlignment(VerticalAlignment.CENTER)
        constraintCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
        constraintCellStyle.setFillForegroundColor(IndexedColors.SKY_BLUE.index)
        val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
        row.createCell(0).setCellValue("--") // task
        row.createCell(1).setCellValue("--") // deliverable
        row.createCell(2).setCellValue("--") // type
        row.createCell(3).setCellValue("--") // duration
        row.createCell(4).setCellValue("bpmn:task:deliverable") // constraint
        row.createCell(5).setCellValue(offset) // offset
        row.createCell(6).setCellValue(duration) // duration
        row.cellIterator().forEachRemaining(_.setCellStyle(constraintCellStyle))
      }
      val deliverableCellStyle = taskSheet.getWorkbook.createCellStyle()
      deliverableCellStyle.setAlignment(HorizontalAlignment.CENTER)
      deliverableCellStyle.setVerticalAlignment(VerticalAlignment.CENTER)
      deliverableCellStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.index)
      deliverableCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
      for (deliverable <- deliverables) {
        val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
        row.createCell(0).setCellValue("--")
        row.createCell(1).setCellValue(deliverable.name[String])
        val deliverableType = deliverable.`type`[String]
        row.createCell(2).setCellValue(deliverableType)
        row.createCell(3).setCellValue(deliverable.duration[Int])
        row.createCell(4).setCellValue("--") // constraint
        row.createCell(5).setCellValue("--") // offset
        row.createCell(6).setCellValue("--") // duration
        row.cellIterator().forEachRemaining(_.setCellStyle(deliverableCellStyle))
        addConstraintRow(taskSheet, "10", "5")
      }
    }
    def createDummyDeliverable(n: Int): Document = {
      val types = Seq(("Labor", 20, 5), ("Material", 10, 0), ("Equipment", 5, 10), ("Work", 30, 0))
      val constraints: Many[String] = Seq("task:deliverable", "bpmn:task:deliverable", "task:deliverable",
        "bpmn:task:deliverable").asJava
      new Document("name", s"sample-deliverable-$n").append("type", types(n - 1)._1).append("duration", types(n - 1)._3)
    }
    val activityIds: Seq[ObjectId] = process.activity_ids[Many[ObjectId]]
    val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityIds),
      "bpmn_name" -> bpmnName))
    val taskSheet = workbook.createSheet(process._id[ObjectId].toString)
    val headerInfo = Seq(("Task", 60), ("Deliverable", 60), ("Type", 20), ("Duration", 20),
      ("Constraint", 60), ("Offset", 20), ("Duration", 20))
    makeHeaderRow(taskSheet, headerInfo)
    for (activity <- activities) {
      addTaskRow(taskSheet, activity.name[String])
      if (activity.has("deliverables")) {
        val deliverables = activity.deliverables[Many[Document]]
        deliverables.foreach(deliverable => deliverable.operation = "delete")
        addDeliverableRow(taskSheet, deliverables)
      } else {
        val testDeliverables: Many[Document] = (1 to 4).map(createDummyDeliverable).asJava
        activity.deliverables = testDeliverables
        addDeliverableRow(taskSheet, activity.deliverables[Many[Document]])
      }
    }
    taskSheet.getLastRowNum + 1
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val processOid = new ObjectId(parameters("process_id"))
      val bpmnName = parameters("bpmn_name")
      val workbook = new XSSFWorkbook()
      val process: DynDoc = ProcessApi.processById(processOid)
      val taskNbr = addTasksSheet(workbook, process, bpmnName)
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
