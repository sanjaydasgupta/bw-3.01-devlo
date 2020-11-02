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
import scala.collection.mutable

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

  private def validateTaskConfigurations(taskConfigurations: Seq[DynDoc], processOid: ObjectId,
      errorList: mutable.Buffer[String]): Unit = {
    val tasksWithoutDeliverables = taskConfigurations.filter(_.deliverables[Seq[DynDoc]].isEmpty)
    if (tasksWithoutDeliverables.nonEmpty) {
      val taskNames = tasksWithoutDeliverables.map(_.name[String]).mkString(", ")
      val taskRows = tasksWithoutDeliverables.map(_.row[Int]).mkString(", ")
      //throw new IllegalArgumentException(s"Found tasks without deliverables: $namesOfTasksWithoutDeliverables")
      errorList.append(s"ERROR: Found tasks without deliverables: $taskNames in rows $taskRows")
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
      //throw new IllegalArgumentException(s"""Found duplicated task names: ${duplicatedTaskNames.mkString(", ")}""")
      errorList.append(s"""ERROR: Found duplicated task names: ${duplicatedTaskNames.mkString(", ")}""")
    }
    val activities = ProcessApi.allActivities(processOid)
    if (activities.length != taskConfigurations.length) {
      //throw new IllegalArgumentException(s"Bad task count - expected ${activities.length}, found ${taskConfigurations.length}")
      errorList.append(s"ERROR: Bad task count - expected ${activities.length}, found ${taskConfigurations.length}")
    }
    val taskNameIdMap: Map[String, ObjectId] =
        activities.map(activity => activity.name[String] -> activity._id[ObjectId]).toMap
    val tasksWithUnexpectedNames = taskConfigurations.filterNot(t => taskNameIdMap.containsKey(t.name[String]))
    if (tasksWithUnexpectedNames.nonEmpty) {
      val taskNames = tasksWithUnexpectedNames.map(_.name[String]).mkString(", ")
      val taskRows = tasksWithUnexpectedNames.map(_.row[Int]).mkString(", ")
      //throw new IllegalArgumentException(s"""Found unexpected task names: ${unexpectedTaskNames.mkString(", ")}""")
      errorList.append(s"ERROR: Found unexpected task names: $taskNames in rows: $taskRows")
    }
    taskConfigurations.foreach(config => config._id = taskNameIdMap.getOrElse(config.name[String], new ObjectId()))
  }

  private def useOneConfigRow(configTasksErrorList: (Seq[DynDoc], mutable.Buffer[String]), configRow: Row):
      (Seq[DynDoc], mutable.Buffer[String]) = {
    val (configTasks, errorList) = configTasksErrorList
    val rowNumber: Int = configRow.getRowNum + 1
    def getCellValue(cell: Cell): String = {
      cell.getCellType match {
        case CellType.NUMERIC => cell.getNumericCellValue.toString.trim
        case CellType.STRING => cell.getStringCellValue.trim
        case cellType =>
          //throw new IllegalArgumentException(s"Bad cell type, found '$cellType' in row $rowNumber")
          errorList.append(s"ERROR: Bad cell type, found '$cellType' in row $rowNumber")
          "ERROR (bad type)!"
      }
    }
    val cellValues: Seq[Option[String]] = configRow.asScala.toSeq.map(getCellValue).
      map(s => if (s.replaceAll("[\\s-]+", "").isEmpty) None else Some(s))
    if (cellValues.length != 10) {
      errorList.append(s"ERROR: Bad row - expected 10 cells, found ${cellValues.length} in row $rowNumber")
      (configTasks, errorList)
    } else {
      cellValues match {
        // Task row
        case Seq(Some(taskName), None, None, None, None, None, None, None, None, None) =>
          val newTask: DynDoc = new Document("name", taskName).append("deliverables", Seq.empty[DynDoc]).
              append("row", rowNumber)
          (newTask +: configTasks, errorList)
        // Deliverable row
        case Seq(None, Some(deliverableName), Some(description), Some(deliverableType), Some(duration), Some(team),
            None, None, None, None) =>
          val oldErrorCount = errorList.length
          if (!deliverableType.matches("(?i)document|work")) {
            //throw new IllegalArgumentException(s"Bad deliverable type, found '$deliverableType' in row $rowNumber")
            errorList.append(s"ERROR: Bad deliverable type, found '$deliverableType' in row $rowNumber")
          }
          if (!duration.matches("[0-9.]+")) {
            //throw new IllegalArgumentException(s"Bad duration value, found '$duration' in row $rowNumber")
            errorList.append(s"ERROR: Bad duration value, found '$duration' in row $rowNumber")
          }
          if (errorList.length == oldErrorCount) {
            val oldErrorCount = errorList.length
            val newDeliverable: DynDoc = new Document("name", deliverableName).append("description", description).
                append("type", deliverableType.toLowerCase).append("duration", duration.toDouble).
                append("team", team).append("constraints", Seq.empty[DynDoc]).append("row", rowNumber)
            val currentTask = configTasks.head
            val currentDeliverables = currentTask.deliverables[Seq[DynDoc]]
            val existingDeliverableNames = currentDeliverables.map(_.name[String])
            if (existingDeliverableNames.contains(deliverableName)) {
              //throw new IllegalArgumentException(s"Duplicate deliverable name, found '$deliverableName' in row $rowNumber")
              errorList.append(s"ERROR: Duplicate deliverable name, found '$deliverableName' in row $rowNumber")
            }
            if (errorList.length == oldErrorCount) {
              currentTask.deliverables = newDeliverable +: currentDeliverables
            }
            (configTasks, errorList)
          } else {
            (configTasks, errorList)
          }
        // Constraint row
        case Seq(None, None, None, None, None, None,
            Some(constraintSpec), Some(constraintType), Some(offset), Some(duration)) =>
          val oldErrorCount = errorList.length
          if (!offset.matches("[0-9.]+")) {
            //throw new IllegalArgumentException(s"Bad offset value, found '$offset' in row $rowNumber")
            errorList.append(s"ERROR: Bad offset value, found '$offset' in row $rowNumber")
          }
          if (!duration.matches("[0-9.]+")) {
            //throw new IllegalArgumentException(s"Bad duration value, found '$duration' in row $rowNumber")
            errorList.append(s"ERROR: Bad duration value, found '$duration' in row $rowNumber")
          }
          if (!constraintType.matches("(?i)labor|material|equipment|work|document")) {
            //throw new IllegalArgumentException(s"Bad deliverable type, found '$deliverableType' in row $rowNumber")
            errorList.append(s"ERROR: Bad deliverable type, found '$constraintType' in row $rowNumber")
          }
          if (errorList.length == oldErrorCount) {
            val oldErrorCount = errorList.length
//            val newConstraint: DynDoc = constraintSpec.split(":").toSeq.map(_.trim) match {
//              case Seq(bpmn, task, deliverable) => new Document("bpmn", bpmn).append("task", task).
//                append("deliverable", deliverable).append("offset", offset.toDouble).append("duration", duration.toDouble).
//                append("row", rowNumber).append("type", constraintType.toLowerCase)
//              case Seq(task, deliverable) => new Document("task", task).append("deliverable", deliverable).
//                append("offset", offset.toDouble).append("duration", duration.toDouble).append("row", rowNumber).
//                append("type", constraintType.toLowerCase)
//              case Seq(deliverable) if constraintType.matches("(?i)work|document") =>
//                new Document("deliverable", deliverable).append("duration", duration.toDouble).
//                append("offset", offset.toDouble).append("row", rowNumber).append("type", constraintType.toLowerCase)
//              case Seq(constraintSpecification) =>
//                new Document("constraint", constraintSpecification).append("duration", duration.toDouble).
//                append("offset", offset.toDouble).append("row", rowNumber).append("type", constraintType.toLowerCase)
//              case _ => //throw new IllegalArgumentException(s"Bad constraint, found '$constraintSpec' in row $rowNumber")
//                errorList.append(s"ERROR: Bad constraint, found '$constraintSpec' in row $rowNumber")
//                new Document("deliverable", "ERROR!!!").append("offset", offset.toDouble).
//                  append("duration", duration.toDouble).append("row", rowNumber)
//            }
              val newConstraint: DynDoc = new Document("constraint", constraintSpec).
                  append("duration", duration.toDouble).append("offset", offset.toDouble).append("row", rowNumber).
                  append("type", constraintType.toLowerCase)
              if (errorList.length == oldErrorCount) {
              val currentTask = configTasks.head
              val currentTaskDeliverables = currentTask.deliverables[Seq[DynDoc]]
              if (currentTaskDeliverables.nonEmpty) {
                val currentDeliverable: DynDoc = currentTaskDeliverables.head
                currentDeliverable.constraints = newConstraint +: currentDeliverable.constraints[Seq[DynDoc]]
                (configTasks, errorList)
              } else {
                errorList.append(s"ERROR: Adding constraint without deliverable in row $rowNumber")
                (configTasks, errorList)
              }
            } else {
              (configTasks, errorList)
            }
          } else {
            (configTasks, errorList)
          }
        // ERROR row
        case _ =>
          throw new IllegalArgumentException(s"Bad values in row $rowNumber")
      }
    }
  }

  private def processTasksConfigurations(taskSheet: Sheet, processOid: ObjectId, errorList: mutable.Buffer[String]):
      Seq[DynDoc] = {
    val oldErrorCount = errorList.length
    val configInfoIterator: Iterator[Row] = taskSheet.rowIterator.asScala
    val header = configInfoIterator.take(1).next()
    val taskHeaderCellCount = header.getPhysicalNumberOfCells
    if (taskHeaderCellCount != 10) {
      errorList.append(s"ERROR: Bad header - expected 10 cells, found $taskHeaderCellCount")
    }
    if (errorList.length == oldErrorCount) {
      val reversedTaskConfigurations: Seq[DynDoc] = configInfoIterator.toSeq.
          foldLeft((Seq.empty[DynDoc], errorList))(useOneConfigRow)._1
      val taskConfigurations = reversedTaskConfigurations.reverse
      for (config <- taskConfigurations) {
        val deliverables = config.deliverables[Seq[DynDoc]]
        for (deliverable <- deliverables) {
          val constraints = deliverable.constraints[Seq[DynDoc]]
          deliverable.constraints = constraints.reverse
        }
        config.deliverables = deliverables.reverse
      }
      validateTaskConfigurations(taskConfigurations, processOid, errorList)
      taskConfigurations
    } else {
      Seq.empty[DynDoc]
    }
  }

  private def doPostTransaction(request: HttpServletRequest): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val processOid = new ObjectId(parameters("process_id"))
      val process: DynDoc = ProcessApi.processById(processOid)
      val user: DynDoc = getUser(request)
      if (!ProcessApi.canManage(user._id[ObjectId], process))
        throw new IllegalArgumentException("Not permitted")
      //val bpmnName = parameters("bpmn_name")
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
      val errorList = mutable.Buffer.empty[String]
      val taskConfigurations = processTasksConfigurations(theSheet, processOid, errorList)
      if (errorList.isEmpty) {
        storeTaskConfigurations(taskConfigurations)
        BWLogger.audit(getClass.getName, request.getMethod, s"Uploaded process $processOid configuration Excel", request)
      } else {
        val errors = errorList.mkString(";\n")
        throw new IllegalArgumentException(errors)
        //response.getWriter.print(errors)
        //response.setContentType("text/plain")
        //BWLogger.log(getClass.getName, request.getMethod, s"EXIT: ${errorList.length} errors found", request)
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    doPostTransaction(request)
  }
}
