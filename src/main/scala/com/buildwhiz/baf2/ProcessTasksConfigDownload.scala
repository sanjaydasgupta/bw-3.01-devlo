package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.apache.poi.ss.usermodel.{HorizontalAlignment, VerticalAlignment}
import org.apache.poi.xssf.usermodel.{XSSFColor, XSSFSheet, XSSFWorkbook}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class ProcessTasksConfigDownload extends HttpServlet with HttpUtils {

  private def makeHeaderRow(taskSheet: XSSFSheet, headerInfo: Seq[(String, Int)]): Unit = {
    val cellStyle = taskSheet.getWorkbook.createCellStyle()
    cellStyle.setAlignment(HorizontalAlignment.CENTER)
    val cellFont = cellStyle.getFont
    cellFont.setBold(true)
    cellStyle.setFont(cellFont)
    cellStyle.setLocked(true)
    cellStyle.setVerticalAlignment(VerticalAlignment.CENTER)
    cellStyle.setWrapText(true)
    cellStyle.setFillBackgroundColor(new XSSFColor(java.awt.Color.cyan))
    val headerRow = taskSheet.createRow(0)
    headerRow.setRowStyle(cellStyle)
    val rowHeight = if (headerInfo.exists(_._1.contains("\n"))) 36 else 18
    headerRow.setHeightInPoints(rowHeight)
    for (hdrInfo <- headerInfo.zipWithIndex) {
      val cell = headerRow.createCell(hdrInfo._2)
      cell.setCellStyle(cellStyle)
      cell.setCellValue(hdrInfo._1._1)
      cell.getSheet.setColumnWidth(hdrInfo._2, hdrInfo._1._2 * 125)
    }
  }

  private def addTasksSheet(workbook: XSSFWorkbook, process: DynDoc, bpmnName: String): Int = {
    def addTaskRow(taskSheet: XSSFSheet, taskName: String): Unit = {
      val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
      row.createCell(0).setCellValue(taskName)
      row.createCell(1).setCellValue("--") // deliverable
      row.createCell(2).setCellValue("--") // type
      row.createCell(3).setCellValue("--") // time-offset
      row.createCell(4).setCellValue("--") // duration
      row.createCell(5).setCellValue("--") // constraint
    }
    def addDeliverableRow(taskSheet: XSSFSheet, deliverables: Seq[DynDoc]): Unit = {
      for (deliverable <- deliverables) {
        val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
        row.createCell(0).setCellValue("--")
        row.createCell(1).setCellValue(deliverable.name[String])
        val deliverableType = deliverable.`type`[String]
        row.createCell(2).setCellValue(deliverableType)
        row.createCell(3).setCellValue(deliverable.offset[Int])
        row.createCell(4).setCellValue(deliverable.duration[Int])
        row.createCell(5).setCellValue(deliverable.constraints[Many[String]].mkString(", "))
      }
    }
    def createDummyDeliverable(n: Int): Document = {
      val types = Seq(("Labor", 20, 5), ("Material", 10, 0), ("Equipment", 5, 10), ("Work", 30, 0))
      val constraints: Many[String] = Seq("task:deliverable", "bpmn:task:deliverable", "task:deliverable",
        "bpmn:task:deliverable").asJava
      new Document("name", s"sample-deliverable-$n").append("type", types(n - 1)._1).append("offset", types(n - 1)._2).
        append("duration", types(n - 1)._3).append("constraints", constraints)
    }
    val activityIds: Seq[ObjectId] = process.activity_ids[Many[ObjectId]]
    val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityIds),
      "bpmn_name" -> bpmnName))
    val taskSheet = workbook.createSheet(process._id[ObjectId].toString)
    val headerInfo = Seq(("Task", 60), ("Deliverable", 60), ("Type", 20), ("Offset", 20), ("Duration", 20),
      ("Constraints", 120))
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
