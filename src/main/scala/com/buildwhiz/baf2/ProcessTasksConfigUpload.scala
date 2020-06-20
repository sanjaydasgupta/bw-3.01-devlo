package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.apache.poi.ss.usermodel.{Row, Sheet}
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class ProcessTasksConfigUpload extends HttpServlet with HttpUtils with MailUtils {

  private def processTasks(request: HttpServletRequest, response: HttpServletResponse, taskSheet: Sheet,
      bpmnName: String): String = {

    def setTask(name: String, description: String, typ: String, activityId: String, duration: String,
        assignee: String, role: String): Unit = {
      val activityOid = new ObjectId(activityId)
      val theActivity: DynDoc = ActivityApi.activityById(activityOid)
      val actions: Seq[DynDoc] = ActivityApi.allActions(theActivity)
      actions.find(_.name[String] == name) match {
        case None =>
          ActionAdd.add(request, activityOid, name, description, typ, bpmnName, new ObjectId(assignee), duration,
            if (role == "???") None else Some(role))
        case Some(action) =>
          if (action.status[String] != "defined") {
            throw new IllegalArgumentException(s"wrong status: '${action.status[String]}'")
          }
          if (typ == "#")
            ActionDelete.delete(request, activityOid, name)
          else
            ActionDurationSet.set(request, activityOid, name, duration, Some(description))
      }
    }

    BWLogger.log(getClass.getName, "processTasks", "ENTRY", request)
    val rows: Iterator[Row] = taskSheet.rowIterator.asScala
    val header = rows.take(1).toSeq.head
    val taskHeaderCellCount = header.getPhysicalNumberOfCells
    if (taskHeaderCellCount != 7)
      throw new IllegalArgumentException(s"unexpected task header cell count ($taskHeaderCellCount)")
    for (row <- rows) {
      row.cellIterator.asScala.toSeq.map(_.getStringCellValue) match {
        case Seq(name, typ, activityId, duration, assignee, role, description) =>
          setTask(name, description, typ, activityId, duration, assignee, role)
        case _ =>
          val message = s"unexpected task cell count (${row.getPhysicalNumberOfCells}) in row ${row.getRowNum + 1}"
          throw new IllegalArgumentException(message)
      }
    }
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
        throw new IllegalArgumentException(s"unexpected number of sheets: $nbrOfSheets")
      val theSheet: Sheet = workbook.sheetIterator.next()
      if (theSheet.getSheetName != processOid.toString)
        throw new IllegalArgumentException(s"Mismatched process_id ($processOid != ${theSheet.getSheetName})")
      val msg = "" //processTasks(request, response, theSheet, bpmnName)
      BWLogger.audit(getClass.getName, "doPost", s"Uploaded process configuration Excel ($nbrOfSheets sheets: $msg)", request)
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
