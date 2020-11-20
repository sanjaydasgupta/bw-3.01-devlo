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
import scala.collection.mutable

class ProjectTeamsConfigUpload extends HttpServlet with HttpUtils with MailUtils {

  private def storeTeamConfigurations(taskConfigurations: Seq[DynDoc]): Unit = {
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

  private def useOneConfigRow(configTeamsErrorList: (Seq[DynDoc], mutable.Buffer[String]), configRow: Row):
      (Seq[DynDoc], mutable.Buffer[String]) = {
    val (configTeams, errorList) = configTeamsErrorList
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
    if (cellValues.length != 5) {
      errorList.append(s"ERROR: Bad row - expected 5 cells, found ${cellValues.length} in row $rowNumber")
      (configTeams, errorList)
    } else {
      cellValues match {
        case Seq(Some(groupName), Some(teamName), omniClassCode: Option[String], color: Option[String],
            organizationName: Option[String]) =>
          organizationName match {
            case None =>
              val newTeam: DynDoc = new Document("group", groupName).append("name", teamName).
                append("omniClass", omniClassCode.getOrElse("--")).append("color", color.getOrElse("__"))
              (newTeam +: configTeams, errorList)
            case Some(orgName) => OrganizationApi.organizationByName(orgName) match {
              case Some(organization) =>
                val newTeam: DynDoc = new Document("group", groupName).append("name", teamName).
                  append("omniClass", omniClassCode.getOrElse("--")).append("color", color.getOrElse("__")).
                  append("organization_id", organization._id[ObjectId])
                (newTeam +: configTeams, errorList)
              case None => // add error info
                errorList.append(s"ERROR: Bad Organization name, found '$orgName' in row $rowNumber")
                (configTeams, errorList)
            }
          }
        // ERROR row
        case _ =>
          throw new IllegalArgumentException(s"Bad values in row $rowNumber")
      }
    }
  }

  private def projectTeamsConfiguration(taskSheet: Sheet, processOid: ObjectId, errorList: mutable.Buffer[String]):
      Seq[DynDoc] = {
    val oldErrorCount = errorList.length
    val configInfoIterator: Iterator[Row] = taskSheet.rowIterator.asScala
    val header = configInfoIterator.take(1).next()
    val taskHeaderCellCount = header.getPhysicalNumberOfCells
    if (taskHeaderCellCount != 5) {
      errorList.append(s"ERROR: Bad header - expected 5 cells, found $taskHeaderCellCount")
    }
    if (errorList.length == oldErrorCount) {
      val reversedTeamConfigurations: Seq[DynDoc] = configInfoIterator.toSeq.
          foldLeft((Seq.empty[DynDoc], errorList))(useOneConfigRow)._1
      val taskConfigurations = reversedTeamConfigurations.reverse
      for (config <- taskConfigurations) {
        val deliverables = config.deliverables[Seq[DynDoc]]
        for (deliverable <- deliverables) {
          val constraints = deliverable.constraints[Seq[DynDoc]]
          deliverable.constraints = constraints.reverse
        }
        config.deliverables = deliverables.reverse
      }
      //validateTaskConfigurations(taskConfigurations, processOid, errorList)
      taskConfigurations
    } else {
      Seq.empty[DynDoc]
    }
  }

  private def doPostTransaction(request: HttpServletRequest): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val project: DynDoc = ProjectApi.projectById(projectOid)
      val user: DynDoc = getUser(request)
      if (!ProcessApi.canManage(user._id[ObjectId], project))
        throw new IllegalArgumentException("Not permitted")
      val parts = request.getParts
      if (parts.size != 1)
        throw new IllegalArgumentException(s"Unexpected number of files ${parts.size}")
      val workbook = new XSSFWorkbook(parts.asScala.head.getInputStream)
      val nbrOfSheets = workbook.getNumberOfSheets
      if (nbrOfSheets != 1)
        throw new IllegalArgumentException(s"bad sheet count - expected 1, got $nbrOfSheets")
      val theSheet: Sheet = workbook.sheetIterator.next()
      if (theSheet.getSheetName != projectOid.toString)
        throw new IllegalArgumentException(s"Mismatched project_id ($projectOid != ${theSheet.getSheetName})")
      val errorList = mutable.Buffer.empty[String]
      val teamConfigurations = projectTeamsConfiguration(theSheet, projectOid, errorList)
      if (errorList.isEmpty) {
        storeTeamConfigurations(teamConfigurations)
        BWLogger.audit(getClass.getName, request.getMethod, s"Uploaded project-team $projectOid configuration Excel", request)
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
