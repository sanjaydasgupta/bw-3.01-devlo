package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.bson.types.ObjectId
import org.bson.Document

class PhaseConfigDownload extends HttpServlet with HttpUtils {

  private def addTasksSheet(workbook: XSSFWorkbook, project: DynDoc, phase: DynDoc, bpmnName: String): Int = {
    val activityIds: Seq[ObjectId] = phase.activity_ids[Many[ObjectId]]
    val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityIds),
      "bpmn_name" -> bpmnName))
    val taskSheet = workbook.createSheet("Tasks")
    val headerRow = taskSheet.createRow(0)
    val headerInfo = Seq(("Task", 60), ("Type", 20), ("Parent", 40), ("Duration", 20), ("Assignee", 50), ("Description", 100))
    for (hdrInfo <- headerInfo.zipWithIndex) {
      val cell = headerRow.createCell(hdrInfo._2)
      cell.setCellValue(hdrInfo._1._1)
      taskSheet.setColumnWidth(hdrInfo._2, hdrInfo._1._2 * 125)
    }
    val projectName = project.name[String]
    val phaseName = phase.name[String]
    for (activity <- activities) {
      val activityName = activity.name[String]
      val actions: Seq[DynDoc] = activity.actions[Many[Document]]
      for (task <- actions) {
        val row = taskSheet.createRow(taskSheet.getLastRowNum + 1)
        val taskName = task.name[String]
        row.createCell(0).setCellValue(s"$taskName ($projectName/$phaseName)")
        val aType = task.`type`[String]
        row.createCell(1).setCellValue(aType)
        row.createCell(2).setCellValue(activityName)
        val duration = task.duration[String]
        row.createCell(3).setCellValue(duration)
        val assignee = task.assignee_person_id[ObjectId]
        row.createCell(4).setCellValue(assignee.toString)
        val description = activity.description[String]
        row.createCell(5).setCellValue(description)
      }
    }
    taskSheet.getLastRowNum + 1
  }

  private def addVariablesAndTimersSheet(workbook: XSSFWorkbook, project: DynDoc, phase: DynDoc, bpmnName: String): Int = {
    val variablesSheet = workbook.createSheet("Variables & Timers")
    val headerRow = variablesSheet.createRow(0)
    val headerInfo = Seq(("Variable/Timer", 20), ("Name", 60), ("Type", 20), ("Value/Duration", 20))
    for (hdrInfo <- headerInfo.zipWithIndex) {
      val cell = headerRow.createCell(hdrInfo._2)
      cell.setCellValue(hdrInfo._1._1)
      variablesSheet.setColumnWidth(hdrInfo._2, hdrInfo._1._2 * 125)
    }
    val projectName = project.name[String]
    val phaseName = phase.name[String]
    val variables: Seq[DynDoc] = phase.variables[Many[Document]].filter(_.bpmn_name[String] == bpmnName)
    val timers: Seq[DynDoc] = phase.timers[Many[Document]].filter(_.bpmn_name[String] == bpmnName)
    for (variable <- variables) {
      val row = variablesSheet.createRow(variablesSheet.getLastRowNum + 1)
      row.createCell(0).setCellValue("VARIABLE")
      val variableName = variable.label[String]
      row.createCell(1).setCellValue(s"$variableName ($projectName/$phaseName)")
      val vType = variable.`type`[String]
      row.createCell(2).setCellValue(s"$vType")
      val value = variable.value[AnyRef].toString
      row.createCell(3).setCellValue(s"$value")
      //val buffer = s"VARIABLE, $variableName ($projectName/$phaseName), $vType, $value"
    }
    for (timer <- timers) {
      val row = variablesSheet.createRow(variablesSheet.getLastRowNum + 1)
      row.createCell(0).setCellValue("TIMER")
      val timerName = timer.name[String]
      row.createCell(1).setCellValue(s"$timerName ($projectName/$phaseName)")
      row.createCell(2).setCellValue("DURATION")
      val duration = timer.duration[String]
      row.createCell(3).setCellValue(duration)
      //val buffer = s"TIMER, $timerName ($projectName/$phaseName), DURATION, $duration"
    }
    variablesSheet.getLastRowNum + 1
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val phaseOid = new ObjectId(parameters("phase_id"))
      val bpmnName = parameters("bpmn_name")
      val workbook = new XSSFWorkbook()
      val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val taskCount = addTasksSheet(workbook, project, phase, bpmnName)
      val vtCount = addVariablesAndTimersSheet(workbook, project, phase, bpmnName)
      workbook.write(response.getOutputStream)
      response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", s"EXIT-OK ($taskCount tasks, $vtCount variables/timers)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
