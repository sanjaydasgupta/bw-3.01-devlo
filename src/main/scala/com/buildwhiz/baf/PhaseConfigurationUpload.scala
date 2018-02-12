package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import org.apache.poi.ss.usermodel.{Cell, Row, Sheet}
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class PhaseConfigurationUpload extends HttpServlet with HttpUtils with MailUtils {

  private def processTasks(taskSheet: Sheet, projectOid: ObjectId, phaseOid: ObjectId, bpmnName: String): Unit = {
    BWLogger.log(getClass.getName, "processTasks", "ENTRY")
    val rows: Iterator[Row] = taskSheet.rowIterator.asScala
    val header = rows.take(1).toSeq.head
    val cellCount = header.getPhysicalNumberOfCells
    if (cellCount != 0)
      throw new IllegalArgumentException(s"unexpected cell column count $cellCount in row ${header.getRowNum + 1}")
    for (row <- rows) {
      val cells: Seq[Cell] = row.cellIterator.asScala.toSeq
      if (cells.length != 0)
        throw new IllegalArgumentException(s"unexpected cell column count $cellCount in row ${header.getRowNum + 1}")
    }
    BWLogger.log(getClass.getName, "processTasks", s"EXIT-OK (${taskSheet.getPhysicalNumberOfRows} rows)")
  }

  private def processVariables(variableSheet: Sheet, projectOid: ObjectId, phaseOid: ObjectId, bpmnName: String): Unit = {
    BWLogger.log(getClass.getName, "processVariables", "ENTRY")
    val rows: Iterator[Row] = variableSheet.rowIterator.asScala
    val header = rows.take(1).toSeq.head
    val cellCount = header.getPhysicalNumberOfCells
    if (cellCount != 0)
      throw new IllegalArgumentException(s"unexpected cell column count $cellCount in row ${header.getRowNum + 1}")
    for (row <- rows) {
      val cells: Seq[Cell] = row.cellIterator.asScala.toSeq
      if (cells.length != 0)
        throw new IllegalArgumentException(s"unexpected cell column count $cellCount in row ${header.getRowNum + 1}")
    }
    BWLogger.log(getClass.getName, "processVariables", s"EXIT-OK (${variableSheet.getPhysicalNumberOfRows} rows)")
  }

  private def processTimers(timersSheet: Sheet, projectOid: ObjectId, phaseOid: ObjectId, bpmnName: String): Unit = {
    BWLogger.log(getClass.getName, "processTimers", "ENTRY")
    val rows: Iterator[Row] = timersSheet.rowIterator.asScala
    val header = rows.take(1).toSeq.head
    val cellCount = header.getPhysicalNumberOfCells
    if (cellCount != 0)
      throw new IllegalArgumentException(s"unexpected cell column count $cellCount in row ${header.getRowNum + 1}")
    for (row <- rows) {
      val cells: Seq[Cell] = row.cellIterator.asScala.toSeq
      if (cells.length != 0)
        throw new IllegalArgumentException(s"unexpected cell column count $cellCount in row ${header.getRowNum + 1}")
    }
    BWLogger.log(getClass.getName, "processTimers", s"EXIT-OK (${timersSheet.getPhysicalNumberOfRows} rows)")
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val phaseOid = new ObjectId(parameters("phase_id"))
      val bpmnName = parameters("bpmn_name")
      val workbook = new XSSFWorkbook(request.getInputStream)
      val nbrOfSheets = workbook.getNumberOfSheets
      if (nbrOfSheets != 3)
        throw new IllegalArgumentException(s"unexpected number of sheets: $nbrOfSheets")
      val sheets: Iterator[Sheet] = workbook.sheetIterator.asScala
      sheets.map(s => (s.getSheetName, s)).foreach {
        case ("Tasks", sheet) => processTasks(sheet, projectOid, phaseOid, bpmnName)
        case ("Variables", sheet) => processVariables(sheet, projectOid, phaseOid, bpmnName)
        case ("Timers", sheet) => processTimers(sheet, projectOid, phaseOid, bpmnName)
        case (other, _) => throw new IllegalArgumentException(s"unexpected sheet name: $other")
      }
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doPost", s"EXIT-OK ($nbrOfSheets sheets)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
