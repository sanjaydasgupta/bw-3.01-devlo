package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.apache.poi.ss.usermodel.{Cell, CellType, Row, Sheet}
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class ProcessTasksConfigUpload extends HttpServlet with HttpUtils with MailUtils {

  case class Constraint(bpmn: Option[String], task: Option[String], deliverable: String, offset: Int, duration: Int)
  case class Deliverable(name: String, `type`: String, duration: Int, constraints: Seq[Constraint])
  case class Task(name: String, deliverables: Seq[Deliverable])

  private def consumeOneConfigRow(configTasks: Seq[Task], configRow:Row): Seq[Task] = {
    val rowNumber = configRow.getRowNum + 1
    def getCellValue(cell: Cell): String = {
      cell.getCellType match {
        case CellType.NUMERIC => cell.getNumericCellValue.toString.replaceAll("\\.0+", "")
        case CellType.STRING => cell.getStringCellValue
        case cellType =>
          throw new IllegalArgumentException(s"Bad cell type, found '$cellType' in row $rowNumber")
      }
    }
    val cellValues: Seq[Option[String]] = configRow.asScala.toSeq.map(getCellValue).map(_.replaceAll("[\\s-]+", "")).
        map(s => if (s.isEmpty) None else Some(s))
    if (cellValues.length != 7)
      throw new IllegalArgumentException(s"Bad row - expected 7 cells, found ${cellValues.length} in row $rowNumber")
    cellValues match {
      // Task row
      case Seq(Some(taskName), None, None, None, None, None, None) =>
        val newTask = Task(taskName, Seq.empty[Deliverable])
        newTask +: configTasks
      // Deliverable row
      case Seq(None, Some(deliverableName), Some(deliverableType), Some(duration), None, None, None) =>
        if (!deliverableType.matches("(?i)equipment|labor|material|work"))
          throw new IllegalArgumentException(s"Bad deliverable type, found '$deliverableType' in row $rowNumber")
        if (!duration.matches("[0-9]+"))
          throw new IllegalArgumentException(s"Bad duration value, found '$duration' in row $rowNumber")
        val newDeliverable = Deliverable(deliverableName, deliverableType.toLowerCase, duration.toInt,
            Seq.empty[Constraint])
        val currentTask = configTasks.head
        val updatedTask = currentTask.copy(deliverables = newDeliverable +: currentTask.deliverables)
        updatedTask +: configTasks.tail
      // Constraint row
      case Seq(None, None, None, None, Some(constraintSpec), Some(offset), Some(duration)) =>
        if (!offset.matches("[0-9]+"))
          throw new IllegalArgumentException(s"Bad offset value, found '$offset' in row $rowNumber")
        if (!duration.matches("[0-9]+"))
          throw new IllegalArgumentException(s"Bad duration value, found '$duration' in row $rowNumber")
        val newConstraint = constraintSpec.split(":").toSeq.map(_.trim) match {
          case Seq(bpmn, task, deliverable) => Constraint(Some(bpmn), Some(task), deliverable, offset.toInt, duration.toInt)
          case Seq(task, deliverable) => Constraint(None, Some(task), deliverable, offset.toInt, duration.toInt)
          case Seq(deliverable) => Constraint(None, None, deliverable, offset.toInt, duration.toInt)
          case _ => throw new IllegalArgumentException(s"Bad constraint, found '$constraintSpec' in row $rowNumber")
        }
        val currentTask = configTasks.head
        val currentDeliverable = currentTask.deliverables.head
        val updatedDeliverable = currentDeliverable.copy(constraints = newConstraint +: currentDeliverable.constraints)
        val updatedTask = currentTask.copy(deliverables = updatedDeliverable +: currentTask.deliverables.tail)
        updatedTask +: configTasks.tail
      // ERROR row
      case _ =>
        throw new IllegalArgumentException(s"Bad values in row $rowNumber")
    }
  }

  private def processTasksConfig(request: HttpServletRequest, response: HttpServletResponse, taskSheet: Sheet,
      bpmnName: String): String = {
    BWLogger.log(getClass.getName, "processTasksConfig", "ENTRY", request)
    val configInfoIterator: Iterator[Row] = taskSheet.rowIterator.asScala
    val header = configInfoIterator.take(1).next()
    val taskHeaderCellCount = header.getPhysicalNumberOfCells
    if (taskHeaderCellCount != 7)
      throw new IllegalArgumentException(s"Bad header - expected 7 cells, found $taskHeaderCellCount")
    val taskConfigurations: Seq[Task] = configInfoIterator.toSeq.foldLeft(Seq.empty[Task])(consumeOneConfigRow)
    s"${taskSheet.getPhysicalNumberOfRows -1} task(s)"
  }

  private def doPostTransaction(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val processOid = new ObjectId(parameters("process_id"))
      val process: DynDoc = ProcessApi.processById(processOid)
      val user: DynDoc = getUser(request)
      if (!ProcessApi.canManage(user._id[ObjectId], process))
        throw new IllegalArgumentException("Not permitted")
      val bpmnName = parameters("bpmn_name")
      val parts = request.getParts
      if (parts.size != 1)
        throw new IllegalArgumentException(s"Unexpected number of files ${parts.size}")
      val workbook = new XSSFWorkbook(parts.asScala.head.getInputStream)
      val nbrOfSheets = workbook.getNumberOfSheets
      if (nbrOfSheets != 1)
        throw new IllegalArgumentException(s"bad sheet count - expected 1, got $nbrOfSheets")
      val theSheet: Sheet = workbook.sheetIterator.next()
      if (theSheet.getSheetName != processOid.toString)
        throw new IllegalArgumentException(s"Mismatched process_id ($processOid != ${theSheet.getSheetName})")
      val msg = processTasksConfig(request, response, theSheet, bpmnName)
      BWLogger.audit(getClass.getName, "doPost", s"Uploaded process configuration Excel ($msg)", request)
      response.getWriter.print(s"""{"nbrOfSheets": "$nbrOfSheets", "items": "$msg"}""")
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    doPostTransaction(request, response)
  }
}
