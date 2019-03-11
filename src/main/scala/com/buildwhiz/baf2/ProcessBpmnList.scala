package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class ProcessBpmnList extends HttpServlet with HttpUtils with DateTimeUtils {

  private def startTime(obj: DynDoc): Option[Long] = {
    if (obj.has("timestamps")) {
      val timestamps: DynDoc = obj.timestamps[Document]
      if (timestamps.has("start"))
        Some(timestamps.start[Long])
      else
        None
    } else {
      None
    }
  }

  private def endTime(obj: DynDoc): Option[Long] = {
    if (obj.has("timestamps")) {
      val timestamps: DynDoc = obj.timestamps[Document]
      if (timestamps.has("end"))
        Some(timestamps.end[Long])
      else
        None
    } else {
      None
    }
  }

  private def time2string(time: Option[Long], user: DynDoc): String = time match {
    case None => "NA"
    case Some(t) => val tz = user.tz[String]
      dateTimeString(t, Some(tz))
  }

  private def listProcesses(process: DynDoc, user: DynDoc): Seq[DynDoc] = {
    val displayStatus = ProcessApi.displayStatus(process)
    val Seq(topStart, topEnd) = Seq(startTime(process), endTime(process)).map(t => time2string(t, user))
    val topLevelProcess: DynDoc = Map("status" -> process.status[String], "display_status" -> displayStatus,
        "bpmn_name" -> process.bpmn_name[String], "start_time" -> topStart, "end_time" -> topEnd)
    val subProcesses: Seq[DynDoc] = process.bpmn_timestamps[Many[Document]].map(ts => {
      val Seq(timeStart, timeEnd) = Seq(startTime(ts), endTime(ts)).map(t => time2string(t, user))
      val displayStatus = (timeStart, timeEnd) match {
        case ("NA", "NA") => "dormant"
        case (_, "NA") => "active"
        case (_, _) => "ended"
        case _ => "NA"

      }
      Map("status" -> ts.status[String], "display_status" -> displayStatus,
        "bpmn_name" -> ts.name[String], "start_time" -> timeStart, "end_time" -> timeEnd)
    })
    topLevelProcess +: subProcesses.sortBy(_.bpmn_name[String])
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val processOid = new ObjectId(parameters("process_id"))
      val process: DynDoc = BWMongoDB3.processes.find(Map("_id" -> processOid)).head
      val processes = listProcesses(process, user)
      response.getWriter.print(processes.map(process => bson2json(process.asDoc)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(this.getClass.getName, request.getMethod, s"EXIT-OK (${processes.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}
