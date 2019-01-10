package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.types.ObjectId
import org.bson.Document

import scala.collection.mutable

class OwnedProcesses extends HttpServlet with HttpUtils with DateTimeUtils {

  private def createProcesses(request: HttpServletRequest, activities: Seq[DynDoc], variables: Seq[DynDoc],
        timers: Seq[DynDoc], personOid: ObjectId, phase: DynDoc): Seq[DynDoc] = {
    val processes = mutable.Map.empty[String, DynDoc]
    for (activity <- activities) {
      val bpmnName = activity.bpmn_name[String]
      if (!processes.contains(bpmnName)) {
        val processDoc = new Document("variables", Nil).append("timers", Nil)
        processes(bpmnName) = processDoc
      } /*else {
        val activitySeq = processes(bpmnName).activities[Seq[Document]]
        processes(bpmnName).activities = activity.asDoc +: activitySeq
      }*/
    }
    for (variable <- variables) {
      val bpmnName = variable.bpmn_name[String]
      if (!processes.contains(bpmnName)) {
        val processDoc = new Document("variables", Seq(variable.asDoc)).append("timers", Nil)
        processes(bpmnName) = processDoc
      } else {
        val variableSeq = processes(bpmnName).variables[Seq[Document]]
        processes(bpmnName).variables = variable.asDoc +: variableSeq
      }
    }
    for (timer <- timers) {
      val bpmnName = timer.bpmn_name[String]
      if (!processes.contains(bpmnName)) {
        val processDoc = new Document("variables", Nil).append("timers", Seq(timer.asDoc))
        processes(bpmnName) = processDoc
      } else {
        val timerSeq = processes(bpmnName).timers[Seq[Document]]
        processes(bpmnName).timers = timer.asDoc +: timerSeq
      }
    }
    for (p <- processes) {
      val bpmnName = p._1
      val myActivities = activities.filter(_.bpmn_name[String] == bpmnName)
      p._2.bpmn_name = bpmnName
      p._2.is_relevant = myActivities.exists(_.is_relevant[Boolean])
      p._2.status = myActivities.map(_.status[String]).foldLeft("defined")((a, b) => (a, b) match {
          case _ if a == b => a
          case ("running", _) => "running"
          case (_, "running") => "running"
          case _ => b
        })
      //if (myActivities.isEmpty/* && phase.has("bpmn_timestamps")*/) {
        p._2.start_time = "0000-00-00 00:00"
        p._2.end_time = "0000-00-00 00:00"
        val bpmnTimestamps: Seq[DynDoc] = phase.bpmn_timestamps[Many[Document]]
        bpmnTimestamps.find(ts => ts.name[String] == bpmnName && ts.has("timestamps")) match {
          case Some(ts) =>
            val timestamps: DynDoc = ts.timestamps[Document]
            val user: DynDoc = getUser(request)
            if (timestamps.has("start")) {
              val start = timestamps.start[Long]
              p._2.start_time = dateTimeString(start, Some(user.tz[String]))
              p._2.status = "running"
            }
            if (timestamps.has("end")) {
              val end = timestamps.end[Long]
              p._2.end_time = dateTimeString(end, Some(user.tz[String]))
              p._2.status = "ended"
            }
          case None =>
            p._2.status = "defined"
        }
      //}
      val actions: Seq[DynDoc] = myActivities.flatMap(_.actions[Many[Document]])
      if (actions.exists(action => action.status[String] == "waiting" && action.assignee_person_id[ObjectId] == personOid))
        p._2.display_status = "waiting"
      else if (actions.exists(action => action.status[String] == "waiting"))
        p._2.display_status = "waiting2"
      else
        p._2.display_status = p._2.status[String]
    }
    processes.values.toSeq
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val personOid = user._id[ObjectId]
      val phaseOid = new ObjectId(parameters("phase_id"))
      val phase: DynDoc = BWMongoDB3.processes.find(Map("_id" -> phaseOid)).head
      val activityOids = phase.activity_ids[Many[ObjectId]]
      val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityOids)))
      activities.foreach(a => a.is_relevant = a.actions[Many[Document]].exists(_.assignee_person_id[ObjectId] == personOid))
      val processes = createProcesses(request, activities, phase.variables[Many[Document]], phase.timers[Many[Document]],
          personOid, phase)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      response.getWriter.print(processes.map(process => bson2json(process.asDoc)).mkString("[", ", ", "]"))
      BWLogger.log(this.getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}
