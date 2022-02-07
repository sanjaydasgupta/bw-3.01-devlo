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

import scala.jdk.CollectionConverters._
import scala.collection.mutable

class ProjectTeamsConfigUpload extends HttpServlet with HttpUtils with MailUtils {

  private def storeTeamConfigurations(projectOid: ObjectId, teamConfigurations: Seq[DynDoc]): Unit = {
    val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid),
      Map($set -> Map("teams" -> teamConfigurations)))
    if (updateResult.getMatchedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
  }

  private def validateTeamConfigurations(teamConfigurations: Seq[DynDoc], processOid: ObjectId,
        errorList: mutable.Buffer[String]): Unit = {
    val providedTeamNames: Seq[String] = teamConfigurations.map(_.name[String])
    val providedTeamNameCounts: Map[String, Int] =
      providedTeamNames.foldLeft(Map.empty[String, Int])((counts, name) => {
        val newCount = if (counts.contains(name))
          counts.map(pair => if (pair._1 == name) (name, pair._2 + 1) else pair)
        else
          Map(name -> 1) ++ counts
        newCount
      })
    val duplicatedTeamNames = providedTeamNameCounts.filter(_._2 > 1).keys
    if (duplicatedTeamNames.nonEmpty) {
      //throw new IllegalArgumentException(s"""Found duplicated task names: ${duplicatedTaskNames.mkString(", ")}""")
      errorList.append(s"""ERROR: Found duplicated team names: ${duplicatedTeamNames.mkString(", ")}""")
    }
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
          throw new IllegalArgumentException(s"Bad cell type, found '$cellType' in row $rowNumber")
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
                append("omniClass", omniClassCode.getOrElse("--")).append("color", color.getOrElse("--"))
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
          errorList.append(s"ERROR: Bad values/format in row $rowNumber")
          (configTeams, errorList)
      }
    }
  }

  private def projectTeamsConfiguration(teamsConfigSheet: Sheet, processOid: ObjectId, errorList: mutable.Buffer[String]):
      Seq[DynDoc] = {
    val oldErrorCount = errorList.length
    val teamConfigInfoIterator: Iterator[Row] = teamsConfigSheet.rowIterator.asScala
    val header = teamConfigInfoIterator.take(1).next()
    val teamHeaderCellCount = header.getPhysicalNumberOfCells
    if (teamHeaderCellCount != 5) {
      errorList.append(s"ERROR: Bad header - expected 5 cells, found $teamHeaderCellCount")
    }
    if (errorList.length == oldErrorCount) {
      val reversedTeamConfigurations: Seq[DynDoc] = teamConfigInfoIterator.toSeq.
          foldLeft((Seq.empty[DynDoc], errorList))(useOneConfigRow)._1
      val teamConfigurations = reversedTeamConfigurations.reverse
      //validateTeamConfigurations(teamConfigurations, processOid, errorList)
      teamConfigurations
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
      val teamsConfigWorkbook = new XSSFWorkbook(parts.asScala.head.getInputStream)
      val nbrOfSheets = teamsConfigWorkbook.getNumberOfSheets
      if (nbrOfSheets != 1)
        throw new IllegalArgumentException(s"bad sheet count - expected 1, got $nbrOfSheets")
      val teamsConfigSheet: Sheet = teamsConfigWorkbook.sheetIterator.next()
      if (teamsConfigSheet.getSheetName != projectOid.toString)
        throw new IllegalArgumentException(s"Mismatched project_id ($projectOid != ${teamsConfigSheet.getSheetName})")
      val errorList = mutable.Buffer.empty[String]
      val teamConfigurations = projectTeamsConfiguration(teamsConfigSheet, projectOid, errorList)
      if (errorList.isEmpty) {
        storeTeamConfigurations(projectOid, teamConfigurations)
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
