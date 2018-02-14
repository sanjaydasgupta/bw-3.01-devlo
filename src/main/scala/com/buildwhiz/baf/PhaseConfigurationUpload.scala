package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import org.apache.poi.ss.usermodel.{Row, Sheet}
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class PhaseConfigurationUpload extends HttpServlet with HttpUtils with MailUtils {

  private def processTasks(taskSheet: Sheet, projectOid: ObjectId, phaseOid: ObjectId, bpmnName: String): Unit = {
    def setTask(name: String, typ: String, parent: String, duration: String, assignee: String, description: String): Unit = {

    }
    BWLogger.log(getClass.getName, "processTasks", "ENTRY")
    val rows: Iterator[Row] = taskSheet.rowIterator.asScala
    val header = rows.take(1).toSeq.head
    val cellCount = header.getPhysicalNumberOfCells
    if (cellCount != 6)
      throw new IllegalArgumentException(s"unexpected cell count ($cellCount) in header row}")
    for (row <- rows) {
      row.cellIterator.asScala.toSeq match {
        case Seq(name, typ, parent, duration, assignee, description) => setTask(name.getStringCellValue,
          typ.getStringCellValue, parent.getStringCellValue, duration.getStringCellValue, assignee.getStringCellValue,
          description.getStringCellValue)
        case _ => throw new IllegalArgumentException(s"unexpected cell count ($cellCount) in row ${header.getRowNum + 1}")
      }
    }
    BWLogger.log(getClass.getName, "processTasks", s"EXIT-OK (${taskSheet.getPhysicalNumberOfRows -1} tasks)")
  }

  private def processVariables(request: HttpServletRequest, response: HttpServletResponse, variableSheet: Sheet,
        projectOid: ObjectId, phaseOid: ObjectId, bpmnName: String): Unit = {
    def setVariable(name: String, typ: String, value: String): Unit = {
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val variables: Option[DynDoc] = phase.variables[Many[Document]].
        find(v => v.bpmn_name[String] == bpmnName && v.label[String] == name)
      if (variables.isEmpty)
        throw new IllegalArgumentException(s"no such variable: $name")
    }
    BWLogger.log(getClass.getName, "processVariables", "ENTRY")
    val rows: Iterator[Row] = variableSheet.rowIterator.asScala
    val header = rows.take(1).toSeq.head
    val cellCount = header.getPhysicalNumberOfCells
    if (cellCount != 3)
      throw new IllegalArgumentException(s"unexpected cell count ($cellCount) in header row")
    for (row <- rows) {
      row.cellIterator.asScala.toSeq match {
        case Seq(name, typ, value) => setVariable(name.getStringCellValue, typ.getStringCellValue, value.getStringCellValue)
        case _ => throw new IllegalArgumentException(s"unexpected cell count ($cellCount) in row ${header.getRowNum + 1}")
      }
    }
    BWLogger.log(getClass.getName, "processVariables", s"EXIT-OK (${variableSheet.getPhysicalNumberOfRows - 1} variables)")
  }

  private def processTimers(request: HttpServletRequest, response: HttpServletResponse, timersSheet: Sheet,
        projectOid: ObjectId, phaseOid: ObjectId, bpmnName: String): Unit = {
    def setTimer(name: String, typ: String, duration: String): Unit = {
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val timers: Option[DynDoc] = phase.timers[Many[Document]].
        find(t => t.bpmn_name[String] == bpmnName && t.name[String] == name)
      if (timers.isEmpty)
        throw new IllegalArgumentException(s"no such timer: $name")
      TimerDurationSet.set(request, response, phaseOid, None, Some(name), bpmnName, duration)
    }
    BWLogger.log(getClass.getName, "processTimers", "ENTRY")
    val rows: Iterator[Row] = timersSheet.rowIterator.asScala
    val header = rows.take(1).toSeq.head
    val cellCount = header.getPhysicalNumberOfCells
    if (cellCount != 3)
      throw new IllegalArgumentException(s"unexpected cell count ($cellCount) in header row")
    for (row <- rows) {
      row.cellIterator.asScala.toSeq match {
        case Seq(name, typ, duration) => setTimer(name.getStringCellValue, typ.getStringCellValue, duration.getStringCellValue)
        case _ => throw new IllegalArgumentException(s"unexpected cell count ($cellCount) in row ${header.getRowNum + 1}")
      }
    }
    BWLogger.log(getClass.getName, "processTimers", s"EXIT-OK (${timersSheet.getPhysicalNumberOfRows - 1} timers)")
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val phaseOid = new ObjectId(parameters("phase_id"))
      val bpmnName = parameters("bpmn_name")
      val parts = request.getParts
      if (parts.size != 1)
        throw new IllegalArgumentException(s"Unexpected number of files ${parts.size}")
      val workbook = new XSSFWorkbook(parts.asScala.head.getInputStream)
      val nbrOfSheets = workbook.getNumberOfSheets
      if (nbrOfSheets != 3)
        throw new IllegalArgumentException(s"unexpected number of sheets: $nbrOfSheets")
      val sheets: Iterator[Sheet] = workbook.sheetIterator.asScala
      sheets.map(s => (s.getSheetName, s)).foreach {
        case ("Tasks", sheet) => processTasks(sheet, projectOid, phaseOid, bpmnName)
        case ("Variables", sheet) => processVariables(request, response, sheet, projectOid, phaseOid, bpmnName)
        case ("Timers", sheet) => processTimers(request, response, sheet, projectOid, phaseOid, bpmnName)
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
