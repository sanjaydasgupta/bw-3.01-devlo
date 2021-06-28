package com.buildwhiz.baf3

import com.buildwhiz.baf2.{OrganizationApi, PersonApi, PhaseApi, ProjectApi}
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.apache.poi.ss.usermodel.{BorderStyle, FillPatternType, HorizontalAlignment, IndexedColors, VerticalAlignment}
import org.apache.poi.xssf.usermodel.{XSSFCellStyle, XSSFSheet, XSSFWorkbook}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class PhaseTeamsContactInfo extends HttpServlet with HttpUtils {

  private def getCellStyle(workbook: XSSFWorkbook, colorIndex: Short): XSSFCellStyle = {
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
    headerRow.setHeightInPoints(rowHeight)
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

  private def addTeamRow(teamsSheet: XSSFSheet, data: Seq[String]): Unit = {
    val row = teamsSheet.createRow(teamsSheet.getLastRowNum + 1)
    val cellStyle = getCellStyle(teamsSheet.getWorkbook, IndexedColors.WHITE.index)
    cellStyle.setLocked(true)
    for ((value, idx) <- data.zipWithIndex) {
      val cell = row.createCell(idx)
      cell.setCellValue(value)
      cell.setCellStyle(cellStyle)
    }
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val optProjectOid = parameters.get("project_id").map(new ObjectId(_))
      val optPhaseOid = parameters.get("phase_id").map(new ObjectId(_))
      //val optActivityOid = parameters.get("activity_id").map(new ObjectId(_))
      val teams = (optProjectOid, optPhaseOid/*, optActivityOid*/) match {
        case (Some(projectOid), None/*, None*/) =>
          val phaseRecords = ProjectApi.allPhases(projectOid)
          val teamOids: Seq[ObjectId] = phaseRecords.flatMap(phaseRecord =>
            phaseRecord.get[Many[Document]]("team_assignments") match {
              case Some(teamAssignments) => teamAssignments.map(_.team_id[ObjectId])
              case None => Seq.empty[ObjectId]
            }
          )
          TeamApi.teamsByIds(teamOids)
        case (None, Some(phaseOid)/*, _*/) =>
          val phaseRecord: DynDoc = PhaseApi.phaseById(phaseOid)
          val teamOids: Seq[ObjectId] = phaseRecord.get[Many[Document]]("team_assignments") match {
            case Some(teamAssignments) => teamAssignments.map(_.team_id[ObjectId])
            case None => Seq.empty[ObjectId]
          }
          TeamApi.teamsByIds(teamOids)
        case _ => Seq.empty[DynDoc]
      }
      val workbook = new XSSFWorkbook()
      val teamsSheet = workbook.createSheet("Teams-Contact-Info.xlsx")
      val headerInfo = Seq(("Team", 30), ("Partner", 30), ("Person", 30), ("Roles", 30), ("Work Phone", 30),
        ("Mobile Phone", 30), ("Email", 30), ("Position", 30))
      addHeaderRow(teamsSheet, headerInfo)
      for (team <- teams) {
        if (team.has("organization_id")) {
          val partner = OrganizationApi.organizationById(team.organization_id[ObjectId])
          val partnerName = partner.name[String]
          if(team.has("team_members")) {
            for (memberInfo <- team.team_members[Many[Document]]) {
              val roles = memberInfo.roles[Many[String]].mkString(", ")
              val personRecord = PersonApi.personById(memberInfo.person_id[ObjectId])
              val personName = PersonApi.fullName(personRecord)
              val workPhone: String = personRecord.phones[Many[Document]].find(_.`type`[String] == "work") match {
                case Some(eml) => eml.phone[String]
                case None => ""
              }
              val mobilePhone: String = personRecord.phones[Many[Document]].find(_.`type`[String] == "mobile") match {
                case Some(eml) => eml.phone[String]
                case None => ""
              }
              val email: String = personRecord.emails[Many[Document]].find(_.`type`[String] == "work") match {
                case Some(eml) => eml.email[String]
                case None => ""
              }
              val position = personRecord.get[String]("position") match {
                case Some(pos) => pos
                case None => ""
              }
              val data = Seq(team.team_name[String], partnerName, personName, roles, workPhone, mobilePhone, email, position)
              addTeamRow(teamsSheet, data)
            }
          }
        }
      }
      workbook.write(response.getOutputStream)
      response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}
