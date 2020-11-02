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
      row.createCell(2).setCellValue("--") // description
      row.createCell(3).setCellValue("--") // type
      row.createCell(4).setCellValue("--") // duration
      row.createCell(5).setCellValue("--") // constraint
      row.createCell(6).setCellValue("--") // type
      row.createCell(7).setCellValue("--") // offset
      row.createCell(8).setCellValue("--") // duration
      val cellStyle = getCellStyle(workbook, IndexedColors.GREY_40_PERCENT.index)
      cellStyle.setLocked(true)
      row.cellIterator().forEachRemaining(_.setCellStyle(cellStyle))
    }

    def addDeliverableRows(taskSheet: XSSFSheet, deliverables: Seq[DynDoc]): Unit = {

      def addConstraintRow(constraint: String, constraintType: String, offset: Double, duration: Double): Unit = {
        val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
        row.createCell(0).setCellValue("--") // task
        row.createCell(1).setCellValue("--") // deliverable
        row.createCell(2).setCellValue("--") // description
        row.createCell(3).setCellValue("--") // type
        row.createCell(4).setCellValue("--") // duration
        //val constraintString = (optBpmn, optTask, deliverable) match {
        //  case(Some(bpmn), Some(task), deliverable) => s"$bpmn:$task:$deliverable"
        //  case(None, Some(task), deliverable) => s"$task:$deliverable"
        //  case(None, None, deliverable) => deliverable
        //  case _ => throw new IllegalArgumentException(s"Internal consistency erroe")
        //}
        row.createCell(5).setCellValue(constraint) // constraint
        row.createCell(6).setCellValue(constraintType) // type
        row.createCell(7).setCellValue(offset) // offset
        row.createCell(8).setCellValue(duration) // duration
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
        if (deliverable.has("description"))
          row.createCell(2).setCellValue(deliverable.description[String])
        else
          row.createCell(2).setCellValue("No description provided")
        row.createCell(3).setCellValue(deliverable.`type`[String])
        row.createCell(4).setCellValue(deliverable.duration[Double])
        row.createCell(5).setCellValue("--") // constraint
        row.createCell(6).setCellValue("--") // type
        row.createCell(7).setCellValue("--") // offset
        row.createCell(8).setCellValue("--") // duration
        row.cellIterator().forEachRemaining(_.setCellStyle(deliverableCellStyle))
        val constraints: Seq[DynDoc] = deliverable.constraints[Many[Document]]
        for (constraint <- constraints) {
          addConstraintRow(constraint.constraint[String], constraint.`type`[String], constraint.offset[Double],
              constraint.duration[Double])
        }
      }
    }

    def createDummyDeliverable(taskName: String, taskType: String): Document = {
      //val deliverableTypes = Seq(("Document", 5d, 10d), ("Work", 30d, 0d))
      val constraintInfo = Seq(
        ("Spec/ID", "Labor", 20d, 5d), ("Spec/ID", "Material", 5d, 0d),
        ("Spec/ID", "Equipment", 5d, 2d), ("Task:Deliverable", "Work", 3d, 0d),
        ("Task:Deliverable", "Document", 3d, 0d)
      )
      val deliverableDurations = Map("Work" -> 10d, "Document" -> 20d)
      val constraints = constraintInfo.map(tuple => new Document("constraint", tuple._1).append("offset", tuple._3).
          append("duration", tuple._4).append("type", tuple._2))
      new Document("name", s"$taskName:sample-$taskType").append("description", "No description provided").
          append("type", taskType).append("duration", deliverableDurations.getOrElse(taskType, 0d)).
          append("constraints", constraints.asJava)
    }

    val activities: Seq[DynDoc] = ProcessApi.allActivities(process._id[ObjectId])
    val taskSheet = workbook.createSheet(process._id[ObjectId].toString)
    val headerInfo = Seq(("Task", 60), ("Deliverable", 60), ("Description", 100), ("Type", 20), ("Duration\n(days)", 20),
      ("Constraint", 60), ("Type", 20), ("Offset\n(days)", 20), ("Duration\n(days)", 20))
    addHeaderRow(taskSheet, headerInfo)
    for (activity <- activities) {
      val taskName = activity.name[String]
      addTaskRow(taskSheet, taskName)
      if (activity.has("deliverables")) {
        val deliverables = activity.deliverables[Many[Document]]
        deliverables.foreach(deliverable => deliverable.operation = "delete")
        addDeliverableRows(taskSheet, deliverables)
      } else {
        val testDeliverables: Many[Document] = Seq("Work", "Document").map(createDummyDeliverable(taskName, _)).asJava
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
