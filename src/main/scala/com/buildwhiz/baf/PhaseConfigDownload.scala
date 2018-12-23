package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.apache.poi.ss.usermodel.{HorizontalAlignment, VerticalAlignment}
import org.apache.poi.xssf.usermodel.{XSSFSheet, XSSFWorkbook}
import org.bson.types.ObjectId
import org.bson.Document

class PhaseConfigDownload extends HttpServlet with HttpUtils {

  private def makeHeaderRow(taskSheet: XSSFSheet, headerInfo: Seq[(String, Int)]): Unit = {
    val cellStyle = taskSheet.getWorkbook.createCellStyle()
    cellStyle.setAlignment(HorizontalAlignment.CENTER)
    val cellFont = cellStyle.getFont
    cellFont.setBold(true)
    cellStyle.setFont(cellFont)
    cellStyle.setLocked(true)
    cellStyle.setVerticalAlignment(VerticalAlignment.CENTER)
    val headerRow = taskSheet.createRow(0)
    headerRow.setRowStyle(cellStyle)
    val rowHeight = if (headerInfo.exists(_._1.contains("\n"))) 36 else 18
    headerRow.setHeightInPoints(rowHeight)
    for (hdrInfo <- headerInfo.zipWithIndex) {
      val cell = headerRow.createCell(hdrInfo._2)
      cell.setCellValue(hdrInfo._1._1)
      cell.getSheet.setColumnWidth(hdrInfo._2, hdrInfo._1._2 * 125)
    }
  }

  private def addTasksSheet(workbook: XSSFWorkbook, phase: DynDoc, bpmnName: String): Int = {
    val activityIds: Seq[ObjectId] = phase.activity_ids[Many[ObjectId]]
    val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityIds),
      "bpmn_name" -> bpmnName))
    val taskSheet = workbook.createSheet("Tasks")
    val headerInfo = Seq(("Task-Name", 60), ("Type", 20), ("Parent", 60), ("Duration", 20), ("Assignee", 50),
      ("Role", 50), ("Description", 100))
    makeHeaderRow(taskSheet, headerInfo)
    for (activity <- activities) {
      val actions: Seq[DynDoc] = activity.actions[Many[Document]]
      val actionRowPairs = actions.map(a => (a, taskSheet.createRow(taskSheet.getLastRowNum + 1)))
      for ((task, row) <- actionRowPairs) {
        val taskName = task.name[String]
        row.createCell(0).setCellValue(s"$taskName")
        val aType = task.`type`[String]
        row.createCell(1).setCellValue(aType)
        // parent's _id ...
        row.createCell(2).setCellValue(activity._id[ObjectId].toString)
        val duration = task.duration[String]
        row.createCell(3).setCellValue(duration)
        val assignee = task.assignee_person_id[ObjectId]
        row.createCell(4).setCellValue(assignee.toString)
        val assigneeRole = if (task.has("assignee_role")) task.assignee_role[String] else "???"
        row.createCell(5).setCellValue(assigneeRole)
        val description = if (task.has("description"))
          task.description[String] else s"${activity.description[String]} ($taskName)"
        row.createCell(6).setCellValue(description)
      }
    }
    taskSheet.getLastRowNum + 1
  }

  private def addVariablesSheet(workbook: XSSFWorkbook, phase: DynDoc, bpmnName: String): Int = {
    val variablesSheet = workbook.createSheet("Variables")
    val headerInfo = Seq(("Variable-Name", 60), ("Type", 20), ("Value", 20))
    makeHeaderRow(variablesSheet, headerInfo)
    val variables: Seq[DynDoc] = phase.variables[Many[Document]].filter(_.bpmn_name[String] == bpmnName)
    for (variable <- variables) {
      val row = variablesSheet.createRow(variablesSheet.getLastRowNum + 1)
      val variableName = variable.label[String]
      row.createCell(0).setCellValue(s"$variableName")
      val vType = variable.`type`[String]
      row.createCell(1).setCellValue(s"$vType")
      val value = variable.value[AnyRef].toString
      row.createCell(2).setCellValue(s"$value")
      //val buffer = s"VARIABLE, $variableName ($projectName/$phaseName), $vType, $value"
    }
    variablesSheet.getLastRowNum + 1
  }

  private def addTimersSheet(workbook: XSSFWorkbook, phase: DynDoc, bpmnName: String): Int = {
    val timersSheet = workbook.createSheet("Timers")
    val headerInfo = Seq(("Timer-Name", 60), ("Type", 20), ("Value", 20))
    makeHeaderRow(timersSheet, headerInfo)
    val timers: Seq[DynDoc] = phase.timers[Many[Document]].filter(_.bpmn_name[String] == bpmnName)
    for (timer <- timers) {
      val row = timersSheet.createRow(timersSheet.getLastRowNum + 1)
      val timerName = timer.name[String]
      row.createCell(0).setCellValue(s"$timerName")
      row.createCell(1).setCellValue("DURATION")
      row.createCell(2).setCellValue(timer.duration[String])
    }
    timersSheet.getLastRowNum + 1
  }

  private def addDocumentsSheet(workbook: XSSFWorkbook, phase: DynDoc, bpmnName: String): Int = {
    val documentSheet = workbook.createSheet("Documents")
    val headerInfo = Seq(("Name", 40), ("Description", 40), ("Labels\nCSVs", 40),
      ("Mandatory\nYes/No", 25), ("Content Type\nimage/pdf/word/excel/bim/xml", 25),
      ("Activity\nDo Not Change", 40), ("Task\nDo Not Change", 40), ("Document-ID\nDo Not Change", 40))
    makeHeaderRow(documentSheet, headerInfo)
    val activityIds: Seq[ObjectId] = phase.activity_ids[Many[ObjectId]]
    val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityIds),
      "bpmn_name" -> bpmnName))
    for (activity <- activities) {
      val actions: Seq[DynDoc] = activity.actions[Many[Document]]
      for (task <- actions) {
        val existingTaskDocs: Seq[DynDoc] = BWMongoDB3.document_master.
          find(Map("activity_id" -> activity._id[ObjectId], "action_name" -> task.name[String]))
        for (doc <- existingTaskDocs) {
          val row = documentSheet.createRow(documentSheet.getLastRowNum + 1)
          row.createCell(0).setCellValue(doc.name[String])
          row.createCell(1).setCellValue(doc.description[String])
          row.createCell(2).setCellValue(doc.labels[Many[String]].mkString(","))
          val mandatory = if (doc.has("mandatory")) {
            if (doc.mandatory[Boolean])
              "yes"
            else
              "no"
          } else
            "no"
          row.createCell(3).setCellValue(mandatory)
          row.createCell(4).setCellValue(doc.content_type[String])
          // owner task reference ...
          row.createCell(5).setCellValue(doc.activity_id[ObjectId].toString)
          row.createCell(6).setCellValue(doc.action_name[String])
          // document _id ...
          row.createCell(7).setCellValue(doc._id[ObjectId].toString)
        }
        if (existingTaskDocs.isEmpty) {
          val row = documentSheet.createRow(documentSheet.getLastRowNum + 1)
          row.createCell(0).setCellValue("???")
          row.createCell(1).setCellValue("???")
          row.createCell(2).setCellValue("???")
          row.createCell(3).setCellValue("???")
          row.createCell(4).setCellValue("???")
          // owner task reference ...
          row.createCell(5).setCellValue(activity._id[ObjectId].toString)
          row.createCell(6).setCellValue(task.name[String])
          // document _id ...
          row.createCell(7).setCellValue("???")
        }
      }
    }
    documentSheet.getLastRowNum + 1
  }

  private def addObserverRolesSheet(workbook: XSSFWorkbook, phase: DynDoc, bpmnName: String): Int = {
    val rolesSheet = workbook.createSheet("Observers")
    val headerInfo = Seq(("Observer-Role", 30), ("Assignee-Name", 60), ("Assignee-Id", 30))
    makeHeaderRow(rolesSheet, headerInfo)
    val assignedRoles: Seq[DynDoc] = if (phase.has("assigned_roles"))
      phase.assigned_roles[Many[Document]] else Seq.empty[DynDoc]
    for (assignedRole <- assignedRoles) {
      val row = rolesSheet.createRow(rolesSheet.getLastRowNum + 1)
      val roleName = assignedRole.role_name[String]
      val personOid = assignedRole.person_id[ObjectId]
      val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
      val personName = s"${person.first_name[String]} ${person.last_name[String]}"
      row.createCell(0).setCellValue(roleName)
      row.createCell(1).setCellValue(personName)
      row.createCell(2).setCellValue(personOid.toString)
    }
    rolesSheet.getLastRowNum + 1
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
      val bpmnName = parameters("bpmn_name")
      val workbook = new XSSFWorkbook()
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val taskNbr = addTasksSheet(workbook, phase, bpmnName)
      val varNbr = addVariablesSheet(workbook, phase, bpmnName)
      val timerNbr = addTimersSheet(workbook, phase, bpmnName)
      val docNbr = addDocumentsSheet(workbook, phase, bpmnName)
      val roleNbr = addObserverRolesSheet(workbook, phase, bpmnName)
      workbook.write(response.getOutputStream)
      response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod,
        s"EXIT-OK ($taskNbr tasks, $varNbr variables, $timerNbr timers, $docNbr documents, $roleNbr roles)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
