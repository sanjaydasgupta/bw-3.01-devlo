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

  private def processTasks(request: HttpServletRequest, response: HttpServletResponse, taskSheet: Sheet,
        bpmnName: String): String = {
    def setTask(name: String, typ: String, parent: String, duration: String, assignee: String, description: String): Unit = {
      val activityOid = new ObjectId(parent)
      val theActivity: DynDoc = BWMongoDB3.activities.find(Map("_id" -> activityOid)).head
      val actions: Seq[DynDoc] = theActivity.actions[Many[Document]]
      if (actions.exists(_.name[String] == name)) {
        if (typ == "#")
          ActionDelete.delete(request, activityOid, name)
        else
          ActionDurationSet.set(request, activityOid, name, duration)
      } else
        ActionAdd.add(request, activityOid, name, typ, bpmnName, new ObjectId(assignee), duration)
    }
    BWLogger.log(getClass.getName, "processTasks", "ENTRY", request)
    val rows: Iterator[Row] = taskSheet.rowIterator.asScala
    val header = rows.take(1).toSeq.head
    val cellCount = header.getPhysicalNumberOfCells
    if (cellCount != 6)
      throw new IllegalArgumentException(s"unexpected cell count ($cellCount) in header row}")
    for (row <- rows) {
      row.cellIterator.asScala.toSeq.map(_.getStringCellValue) match {
        case Seq(name, typ, parent, duration, assignee, description) =>
          setTask(name, typ, parent, duration, assignee, description)
        case _ => throw new IllegalArgumentException(s"unexpected cell count ($cellCount) in row ${header.getRowNum + 1}")
      }
    }
    s"${taskSheet.getPhysicalNumberOfRows -1} task(s)"
  }

  private def processVariables(request: HttpServletRequest, response: HttpServletResponse, variableSheet: Sheet,
        phaseOid: ObjectId, bpmnName: String): String = {
    def setVariable(name: String, typ: String, value: String): Unit = {
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val variables: Option[DynDoc] = phase.variables[Many[Document]].
        find(v => v.bpmn_name[String] == bpmnName && v.label[String] == name)
      if (variables.isEmpty)
        throw new IllegalArgumentException(s"no such variable: $name")
      VariableValueSet.set(request, response, phaseOid, name, bpmnName, value)
    }
    BWLogger.log(getClass.getName, "processVariables", "ENTRY", request)
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
    s"${variableSheet.getPhysicalNumberOfRows - 1} variable(s)"
  }

  private def processTimers(request: HttpServletRequest, response: HttpServletResponse, timersSheet: Sheet,
        phaseOid: ObjectId, bpmnName: String): String = {
    def setTimer(name: String, typ: String, duration: String): Unit = {
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val timers: Option[DynDoc] = phase.timers[Many[Document]].
        find(t => t.bpmn_name[String] == bpmnName && t.name[String] == name)
      if (timers.isEmpty)
        throw new IllegalArgumentException(s"no such timer: $name")
      TimerDurationSet.set(request, phaseOid, None, Some(name), bpmnName, duration)
    }
    BWLogger.log(getClass.getName, "processTimers", "ENTRY", request)
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
    s"${timersSheet.getPhysicalNumberOfRows - 1} timer(s)"
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
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
      val itemsUploaded: String = sheets.map(s => (s.getSheetName, s)).map({
        case ("Tasks", sheet) => processTasks(request, response, sheet, bpmnName)
        case ("Variables", sheet) => processVariables(request, response, sheet, phaseOid, bpmnName)
        case ("Timers", sheet) => processTimers(request, response, sheet, phaseOid, bpmnName)
        case (other, _) => throw new IllegalArgumentException(s"unexpected sheet name: $other")
      }).mkString(", ")
      BWLogger.audit(getClass.getName, "doPost", s"Uploaded phase configuration Excel ($nbrOfSheets sheets: $itemsUploaded)", request)
      response.getWriter.print(s"""{"nbrOfSheets": "$nbrOfSheets", "items": "$itemsUploaded"}""")
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
