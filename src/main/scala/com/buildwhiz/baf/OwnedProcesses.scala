package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.HttpUtils
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.types.ObjectId
import org.bson.Document

import scala.collection.mutable

class OwnedProcesses extends HttpServlet with HttpUtils {

  private def createProcesses(activities: Seq[DynDoc], variables: Seq[DynDoc], timers: Seq[DynDoc],
        personOid: ObjectId): Seq[DynDoc] = {
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
      val actions: Seq[DynDoc] = myActivities.flatMap(_.actions[DocumentList])
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
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      import scala.collection.JavaConverters._
      val personOid = new ObjectId(parameters("person_id"))
      val phaseOid = new ObjectId(parameters("phase_id"))
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).asScala.head
      val activityOids = phase.activity_ids[ObjectIdList]
      val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityOids))).asScala.
        toSeq.map(a => OwnedProcesses.processActivity(a, personOid))
      val processes = createProcesses(activities, phase.variables[DocumentList], phase.timers[DocumentList], personOid)
      writer.print(processes.map(process => bson2json(process.asDoc)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(this.getClass.getName, "doGet()", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

}

object OwnedProcesses {

  def processActivity(activity: DynDoc, personOid: ObjectId): DynDoc = {
    activity.is_relevant = activity.actions[DocumentList].exists(_.assignee_person_id[ObjectId] == personOid)
    activity
  }

}