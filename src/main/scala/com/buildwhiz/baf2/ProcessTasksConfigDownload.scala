package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.apache.poi.ss.usermodel.{HorizontalAlignment, VerticalAlignment}
import org.apache.poi.xssf.usermodel.{XSSFSheet, XSSFWorkbook}
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
    val headerRow = taskSheet.createRow(0)
    headerRow.setRowStyle(cellStyle)
    val rowHeight = if (headerInfo.exists(_._1.contains("\n"))) 36 else 18
    headerRow.setHeightInPoints(rowHeight)
    for (hdrInfo <- headerInfo.zipWithIndex) {
      val cell = headerRow.createCell(hdrInfo._2)
      cell.setCellValue(hdrInfo._1._1)
      cell.getSheet.setColumnWidth(hdrInfo._2, hdrInfo._1._2 * 125)
    }
  }

  private def addTasksSheet(workbook: XSSFWorkbook, process: DynDoc, bpmnName: String): Int = {
    def addTaskRow(taskSheet: XSSFSheet, taskName: String): Unit = {
      val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
      row.createCell(0).setCellValue(taskName)
      row.createCell(1).setCellValue("--")
      row.createCell(2).setCellValue("--")
      row.createCell(3).setCellValue("--")
    }
    def addDeliverableRow(taskSheet: XSSFSheet, deliverables: Seq[DynDoc]): Unit = {
      for (deliverable <- deliverables) {
        val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
        row.createCell(0).setCellValue("--")
        row.createCell(1).setCellValue(deliverable.name[String])
        row.createCell(2).setCellValue(deliverable.`type`[String])
        row.createCell(3).setCellValue(deliverable.constraints[Many[String]].mkString(", "))
      }
    }
    val activityIds: Seq[ObjectId] = process.activity_ids[Many[ObjectId]]
    val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityIds),
      "bpmn_name" -> bpmnName))
    val taskSheet = workbook.createSheet(s"$bpmnName=${process._id[ObjectId]}")
    val headerInfo = Seq(("Task", 60), ("Deliverable", 60), ("Type", 20), ("Constraints", 60))
    makeHeaderRow(taskSheet, headerInfo)
    for (activity <- activities) {
      addTaskRow(taskSheet, activity.name[String])
      if (activity.has("deliverables")) {
        addDeliverableRow(taskSheet, activity.deliverables[Many[Document]])
      } else {
        val constraints: Many[String] = Seq("sample-task?:deliverable?", "sample-bpmn?:task?:deliverable?",
          "sample-task?:deliverable?", "sample-bpmn?:task?:deliverable?").asJava
        val testDeliverables: Many[Document] = (1 to 4).
            map(n => new Document("name", s"sample-deliverable#$n").append("type", s"sample-type-$n").
                append("constraints", constraints)).asJava
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
