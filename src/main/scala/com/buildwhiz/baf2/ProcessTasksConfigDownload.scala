package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.apache.poi.ss.usermodel.{BorderStyle, FillPatternType, HorizontalAlignment, IndexedColors, VerticalAlignment}
import org.apache.poi.xssf.usermodel.{XSSFCellStyle, XSSFSheet, XSSFWorkbook}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class ProcessTasksConfigDownload extends HttpServlet with HttpUtils {

  def getCellStyle(workbook: XSSFWorkbook, colorIndex: Short): XSSFCellStyle = {
    val cellStyle = workbook.createCellStyle()
    cellStyle.setAlignment(HorizontalAlignment.CENTER)
    cellStyle.setVerticalAlignment(VerticalAlignment.CENTER)
    cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
    cellStyle.setFillForegroundColor(colorIndex)
    cellStyle.setBorderRight(BorderStyle.THIN)
    cellStyle.setRightBorderColor(IndexedColors.GREY_40_PERCENT.index)
    cellStyle.setBorderLeft(BorderStyle.THIN)
    cellStyle.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.index)
    cellStyle
  }

  private def addHeaderRow(taskSheet: XSSFSheet, headerInfo: Seq[(String, Int)]): Unit = {
    val headerRow = taskSheet.createRow(0)
    val rowHeight = if (headerInfo.exists(_._1.contains("\n"))) 36 else 18
    headerRow.setHeightInPoints(rowHeight)
    for (hdrInfo <- headerInfo.zipWithIndex) {
      val cell = headerRow.createCell(hdrInfo._2)
      cell.setCellValue(hdrInfo._1._1)
      cell.getSheet.setColumnWidth(hdrInfo._2, hdrInfo._1._2 * 125)
      val cellStyle = getCellStyle(taskSheet.getWorkbook, IndexedColors.GREY_25_PERCENT.index)
      val cellFont = cellStyle.getFont
      cellFont.setBold(true)
      cellStyle.setFont(cellFont)
      //cellStyle.setWrapText(true)
      cellStyle.setLocked(true)
      cell.setCellStyle(cellStyle)
    }
  }

  private def addTasksConfigSheet(workbook: XSSFWorkbook, process: DynDoc): Int = {

    def addTaskRow(taskSheet: XSSFSheet, taskName: String): Unit = {
      val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
      row.createCell(0).setCellValue(taskName)
      row.createCell(1).setCellValue("--") // deliverable
      row.createCell(2).setCellValue("--") // type
      row.createCell(3).setCellValue("--") // duration
      row.createCell(4).setCellValue("--") // constraint
      row.createCell(5).setCellValue("--") // duration
      row.createCell(6).setCellValue("--") // constraint
      val cellStyle = getCellStyle(workbook, IndexedColors.GREY_40_PERCENT.index)
      cellStyle.setLocked(true)
      row.cellIterator().forEachRemaining(_.setCellStyle(cellStyle))
    }

    def addDeliverableRows(taskSheet: XSSFSheet, deliverables: Seq[DynDoc]): Unit = {

      def addConstraintRow(optBpmn: Option[String], optTask: Option[String], deliverable: String, offset: Double,
          duration: Double): Unit = {
        val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
        row.createCell(0).setCellValue("--") // task
        row.createCell(1).setCellValue("--") // deliverable
        row.createCell(2).setCellValue("--") // type
        row.createCell(3).setCellValue("--") // duration
        val constraintString = (optBpmn, optTask, deliverable) match {
          case(Some(bpmn), Some(task), deliverable) => s"$bpmn:$task:$deliverable"
          case(None, Some(task), deliverable) => s"$task:$deliverable"
          case(None, None, deliverable) => deliverable
          case _ => throw new IllegalArgumentException(s"Internal consistency erroe")
        }
        row.createCell(4).setCellValue(constraintString) // constraint
        row.createCell(5).setCellValue(offset) // offset
        row.createCell(6).setCellValue(duration) // duration
        //val constraintCellStyle = getCellStyle(taskSheet.getWorkbook, IndexedColors.LIGHT_CORNFLOWER_BLUE.index)
        val constraintCellStyle = getCellStyle(taskSheet.getWorkbook, IndexedColors.LIGHT_BLUE.index)
        row.cellIterator().forEachRemaining(_.setCellStyle(constraintCellStyle))
      }

      //val deliverableCellStyle = getCellStyle(taskSheet.getWorkbook, IndexedColors.CORNFLOWER_BLUE.index)
      val deliverableCellStyle = getCellStyle(taskSheet.getWorkbook, IndexedColors.PALE_BLUE.index)
      for (deliverable <- deliverables) {
        val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
        row.createCell(0).setCellValue("--")
        row.createCell(1).setCellValue(deliverable.name[String])
        val deliverableType = deliverable.`type`[String]
        row.createCell(2).setCellValue(deliverableType)
        row.createCell(3).setCellValue(deliverable.duration[Double])
        row.createCell(4).setCellValue("--") // constraint
        row.createCell(5).setCellValue("--") // offset
        row.createCell(6).setCellValue("--") // duration
        row.cellIterator().forEachRemaining(_.setCellStyle(deliverableCellStyle))
        val constraints: Seq[DynDoc] = deliverable.constraints[Many[Document]]
        for (constraint <- constraints) {
          addConstraintRow(constraint.get[String]("bpmn"), constraint.get[String]("task"),
              constraint.deliverable[String], constraint.offset[Double], constraint.duration[Double])
        }
      }
    }

    def createDummyDeliverable(taskName: String, n: Int): Document = {
      val types = Seq(("Labor", 20d, 5d), ("Material", 10d, 0d), ("Equipment", 5d, 10d), ("Work", 30d, 0d))
      val constraint = n % 3 match {
        case 0 => new Document("bpmn", "bpmn").append("task", "task").append("deliverable", "deliverable").
            append("offset", 0d).append("duration", 10d)
        case 1 => new Document("task", "task").append("deliverable", "deliverable").
            append("offset", 5d).append("duration", 5d)
        case 2 => new Document("deliverable", "deliverable").append("offset", 10d).append("duration", 0d)
      }
      new Document("name", s"$taskName:sample-deliverable-$n").append("type", types(n - 1)._1).
          append("duration", types(n - 1)._3).append("constraints", Seq(constraint).asJava)
    }

    val activities: Seq[DynDoc] = ProcessApi.allActivities(process._id[ObjectId])
    val taskSheet = workbook.createSheet(process._id[ObjectId].toString)
    val headerInfo = Seq(("Task", 60), ("Deliverable", 60), ("Type", 20), ("Duration\n(days)", 20),
      ("Constraint", 60), ("Offset\n(days)", 20), ("Duration\n(days)", 20))
    addHeaderRow(taskSheet, headerInfo)
    for (activity <- activities) {
      val taskName = activity.name[String]
      addTaskRow(taskSheet, taskName)
      if (activity.has("deliverables")) {
        val deliverables = activity.deliverables[Many[Document]]
        deliverables.foreach(deliverable => deliverable.operation = "delete")
        addDeliverableRows(taskSheet, deliverables)
      } else {
        val testDeliverables: Many[Document] = (1 to 4).map(n => createDummyDeliverable(taskName, n)).asJava
        activity.deliverables = testDeliverables
        addDeliverableRows(taskSheet, activity.deliverables[Many[Document]])
      }
    }
    taskSheet.getLastRowNum + 1
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val processOid = new ObjectId(parameters("process_id"))
      //val bpmnName = parameters("bpmn_name")
      val workbook = new XSSFWorkbook()
      val process: DynDoc = ProcessApi.processById(processOid)
      val taskNbr = addTasksConfigSheet(workbook, process)
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
