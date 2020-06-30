package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.apache.poi.ss.usermodel.{Cell, CellType, Row, Sheet}
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class ProcessTasksConfigUpload extends HttpServlet with HttpUtils with MailUtils {

  private def storeTaskConfigurations(taskConfigurations: Seq[DynDoc]): Unit = {
    for (taskConfig <- taskConfigurations) {
      val activityOid = taskConfig._id[ObjectId]
      val deliverables = taskConfig.deliverables[Seq[DynDoc]]
      val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> activityOid),
          Map($set -> Map("deliverables" -> deliverables)))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    }
  }

  private def validateTaskConfigurations(taskConfigurations: Seq[DynDoc], processOid: ObjectId): Unit = {
    val tasksWithoutDeliverables = taskConfigurations.filter(_.deliverables[Seq[DynDoc]].isEmpty)
    if (tasksWithoutDeliverables.nonEmpty) {
      val namesOfTasksWithoutDeliverables = tasksWithoutDeliverables.map(_.name[String]).mkString(", ")
      throw new IllegalArgumentException(s"Found tasks without deliverables: $namesOfTasksWithoutDeliverables")
    }
    val providedTaskNames: Seq[String] = taskConfigurations.map(_.name[String])
    val providedTaskNameCounts: Map[String, Int] =
      providedTaskNames.foldLeft(Map.empty[String, Int])((counts, name) => {
        val newCount = if (counts.contains(name))
          counts.map(pair => if (pair._1 == name) (name, pair._2 + 1) else pair)
        else
          Map(name -> 1) ++ counts
        newCount
      })
    val duplicatedTaskNames = providedTaskNameCounts.filter(_._2 > 1).keys
    if (duplicatedTaskNames.nonEmpty) {
      throw new IllegalArgumentException(s"""Found duplicated task names: ${duplicatedTaskNames.mkString(", ")}""")
    }
    val activities = ProcessApi.allActivities(processOid)
    if (activities.length != taskConfigurations.length)
      throw new IllegalArgumentException(s"Bad task count - expected ${activities.length}, found ${taskConfigurations.length}")
    val taskNameIdMap: Map[String, ObjectId] =
        activities.map(activity => activity.name[String] -> activity._id[ObjectId]).toMap
    val unexpectedTaskNames = taskConfigurations.map(_.name[String]).filterNot(taskNameIdMap.containsKey)
    if (unexpectedTaskNames.nonEmpty) {
      throw new IllegalArgumentException(s"""Found unexpected task names: ${unexpectedTaskNames.mkString(", ")}""")
    }
    taskConfigurations.foreach(config => config._id = taskNameIdMap(config.name[String]))
  }

  private def useOneConfigRow(configTasks: Seq[DynDoc], configRow:Row): Seq[DynDoc] = {
    val rowNumber = configRow.getRowNum + 1
    def getCellValue(cell: Cell): String = {
      cell.getCellType match {
        case CellType.NUMERIC => cell.getNumericCellValue.toString.trim
        case CellType.STRING => cell.getStringCellValue.trim
        case cellType =>
          throw new IllegalArgumentException(s"Bad cell type, found '$cellType' in row $rowNumber")
      }
    }
    val cellValues: Seq[Option[String]] = configRow.asScala.toSeq.map(getCellValue).
      map(s => if (s.replaceAll("[\\s-]+", "").isEmpty) None else Some(s))
    if (cellValues.length != 7)
      throw new IllegalArgumentException(s"Bad row - expected 7 cells, found ${cellValues.length} in row $rowNumber")
    cellValues match {
      // Task row
      case Seq(Some(taskName), None, None, None, None, None, None) =>
        val newTask: DynDoc = new Document("name", taskName).append("deliverables", Seq.empty[DynDoc])
        newTask +: configTasks
      // Deliverable row
      case Seq(None, Some(deliverableName), Some(deliverableType), Some(duration), None, None, None) =>
        if (!deliverableType.matches("(?i)equipment|labor|material|work"))
          throw new IllegalArgumentException(s"Bad deliverable type, found '$deliverableType' in row $rowNumber")
        if (!duration.matches("[0-9.]+"))
          throw new IllegalArgumentException(s"Bad duration value, found '$duration' in row $rowNumber")
        val newDeliverable: DynDoc = new Document("name", deliverableName).append("type", deliverableType.toLowerCase).
            append("duration", duration.toDouble).append("constraints", Seq.empty[DynDoc])
        val currentTask = configTasks.head
        val currentDeliverables = currentTask.deliverables[Seq[DynDoc]]
        val existingDeliverableNames = currentDeliverables.map(_.name[String])
        if (existingDeliverableNames.contains(deliverableName))
          throw new IllegalArgumentException(s"Duplicate deliverable name, found '$deliverableName' in row $rowNumber")
        currentTask.deliverables = newDeliverable +: currentDeliverables
        configTasks
      // Constraint row
      case Seq(None, None, None, None, Some(constraintSpec), Some(offset), Some(duration)) =>
        if (!offset.matches("[0-9.]+"))
          throw new IllegalArgumentException(s"Bad offset value, found '$offset' in row $rowNumber")
        if (!duration.matches("[0-9.]+"))
          throw new IllegalArgumentException(s"Bad duration value, found '$duration' in row $rowNumber")
        val newConstraint: DynDoc = constraintSpec.split(":").toSeq.map(_.trim) match {
          case Seq(bpmn, task, deliverable) => new Document("bpmn", bpmn).append("task", task).
              append("deliverable", deliverable).append("offset", offset.toDouble).append("duration", duration.toDouble)
          case Seq(task, deliverable) => new Document("task", task).append("deliverable", deliverable).
              append("offset", offset.toDouble).append("duration", duration.toDouble)
          case Seq(deliverable) => new Document("deliverable", deliverable).append("offset", offset.toDouble).
              append("duration", duration.toDouble)
          case _ => throw new IllegalArgumentException(s"Bad constraint, found '$constraintSpec' in row $rowNumber")
        }
        val currentTask = configTasks.head
        val currentDeliverable: DynDoc = currentTask.deliverables[Seq[DynDoc]].head
        currentDeliverable.constraints = newConstraint +: currentDeliverable.constraints[Seq[DynDoc]]
        configTasks
      // ERROR row
      case _ =>
        throw new IllegalArgumentException(s"Bad values in row $rowNumber")
    }
  }

  private def processTasksConfigurations(taskSheet: Sheet, processOid: ObjectId): Seq[DynDoc] = {
    val configInfoIterator: Iterator[Row] = taskSheet.rowIterator.asScala
    val header = configInfoIterator.take(1).next()
    val taskHeaderCellCount = header.getPhysicalNumberOfCells
    if (taskHeaderCellCount != 7)
      throw new IllegalArgumentException(s"Bad header - expected 7 cells, found $taskHeaderCellCount")
    val taskConfigurations: Seq[DynDoc] = configInfoIterator.toSeq.foldLeft(Seq.empty[DynDoc])(useOneConfigRow)
    validateTaskConfigurations(taskConfigurations, processOid)
    taskConfigurations
  }

  private def doPostTransaction(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
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
      val taskConfigurations = processTasksConfigurations(theSheet, processOid)
      storeTaskConfigurations(taskConfigurations)
      BWLogger.audit(getClass.getName, request.getMethod, s"Uploaded process $processOid configuration Excel", request)
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    doPostTransaction(request, response)
  }
}
