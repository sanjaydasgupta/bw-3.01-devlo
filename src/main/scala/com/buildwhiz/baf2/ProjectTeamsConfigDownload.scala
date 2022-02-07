package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.apache.poi.ss.usermodel._
import org.apache.poi.xssf.usermodel.{XSSFCellStyle, XSSFSheet, XSSFWorkbook}
import org.bson.Document
import org.bson.types.ObjectId

class ProjectTeamsConfigDownload extends HttpServlet with HttpUtils {

  def getCellStyle(workbook: XSSFWorkbook, colorIndex: Short): XSSFCellStyle = {
    val cellStyle = workbook.createCellStyle()
    cellStyle.setAlignment(HorizontalAlignment.CENTER)
    cellStyle.setVerticalAlignment(VerticalAlignment.CENTER)
    cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND)
    cellStyle.setFillForegroundColor(colorIndex)
    cellStyle.setBorderRight(BorderStyle.THIN)
    cellStyle.setRightBorderColor(IndexedColors.GREY_40_PERCENT.index)
    cellStyle.setBorderLeft(BorderStyle.THIN)
    cellStyle.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.index)
    cellStyle
  }

  private def addHeaderRow(teamsSheet: XSSFSheet, headerInfo: Seq[(String, Int)]): Unit = {
    val headerRow = teamsSheet.createRow(0)
    val rowHeight = if (headerInfo.exists(_._1.contains("\n"))) 36 else 18
    headerRow.setHeightInPoints(rowHeight.toFloat)
    for (hdrInfo <- headerInfo.zipWithIndex) {
      val cell = headerRow.createCell(hdrInfo._2)
      cell.setCellValue(hdrInfo._1._1)
      cell.getSheet.setColumnWidth(hdrInfo._2, hdrInfo._1._2 * 125)
      val cellStyle = getCellStyle(teamsSheet.getWorkbook, IndexedColors.GREY_25_PERCENT.index)
      val cellFont = cellStyle.getFont
      cellFont.setBold(true)
      cellStyle.setFont(cellFont)
      //cellStyle.setWrapText(true)
      cellStyle.setLocked(true)
      cell.setCellStyle(cellStyle)
    }
  }

  private def addTeamsConfigSheet(workbook: XSSFWorkbook, project: DynDoc): Int = {

    def addTeamRow(taskSheet: XSSFSheet, team: DynDoc): Unit = {
      val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
      // Group, TeamName, Omniclass, Color, Organization
      row.createCell(0).setCellValue(team.group[String])
      row.createCell(1).setCellValue(team.name[String])
      row.createCell(2).setCellValue(team.omniClass[String])
      row.createCell(3).setCellValue(team.color[String])
      val organizationName: String = team.organization_id[AnyRef] match {
        case stringName: String => stringName
        case oid: ObjectId => OrganizationApi.organizationById(oid).name[String]
      }
      row.createCell(4).setCellValue(organizationName)
      val cellStyle = getCellStyle(workbook, IndexedColors.WHITE.index)
      cellStyle.setLocked(true)
      row.cellIterator().forEachRemaining(_.setCellStyle(cellStyle))
    }

    val dummyTeams: Seq[DynDoc] = Seq(
      Map("group" -> "Sample-Group-Name", "name" -> "Sample-Team-Name", "omniClass" -> "Sample-OmniClass-Code",
        "color" -> "Any-HTML-Color", "organization_id" -> "Defined-Organization-Name"),
      Map("group" -> "Sample-Group-Name", "name" -> "Sample-Team-Name", "omniClass" -> "Sample-OmniClass-Code",
        "color" -> "royalblue", "organization_id" -> "Defined-Organization-Name"),
      Map("group" -> "Sample-Group-Name", "name" -> "Sample-Team-Name", "omniClass" -> "Sample-OmniClass-Code",
        "color" -> "chartreuse", "organization_id" -> "--"),
      Map("group" -> "Sample-Group-Name", "name" -> "Sample-Team-Name", "omniClass" -> "Sample-OmniClass-Code",
        "color" -> "--", "organization_id" -> "--"),
      Map("group" -> "Sample-Group-Name", "name" -> "Sample-Team-Name", "omniClass" -> "--",
        "color" -> "--", "organization_id" -> "--")
    )

    val teamsSheet = workbook.createSheet(project._id[ObjectId].toString)
    // Group, TeamName, OmniclassCode, Color, Organization
    val headerInfo = Seq(("Group", 60), ("Team-Name", 60), ("Omniclass-Code", 40), ("Color", 30), ("Organization-Name", 60))
    addHeaderRow(teamsSheet, headerInfo)
    val teams: Seq[DynDoc] = if (project.has("teams")) project.teams[Many[Document]] else dummyTeams
    for (team <- teams) {
      addTeamRow(teamsSheet, team)
    }
    teamsSheet.getLastRowNum
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val workbook = new XSSFWorkbook()
      val project: DynDoc = ProjectApi.projectById(projectOid)
      val teamsCount = addTeamsConfigSheet(workbook, project)
      workbook.write(response.getOutputStream)
      response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK ($teamsCount tasks)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
