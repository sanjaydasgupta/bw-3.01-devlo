package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.utils._
import org.bson.types.ObjectId
import org.bson.Document
import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._

import scala.collection.mutable

class PhaseBpmnXml extends HttpServlet with HttpUtils with BpmnUtils with DateTimeUtils with ProjectUtils {

  private def getVariables(phase: DynDoc, processName: String): Seq[Document] = {
    val variables: Seq[DynDoc] = phase.variables[Many[Document]].filter(_.bpmn_name[String] == processName)
    variables.map(variable => {
      variable.asDoc
    })
  }

  private def getTimers(phase: DynDoc, processName: String): Seq[Document] = {
    val timers: Seq[DynDoc] = phase.timers[Many[Document]].filter(_.bpmn_name[String] == processName)
    timers.map(timer => {
      new Document("bpmn_id", timer.bpmn_id[String]).append("id", timer.bpmn_id[String]).
        append("duration", timer.duration[String]).append("name", timer.name[String]).
        append("status", timer.status[String])
    })
  }

  private def getActivities(phase: DynDoc, processName: String): Seq[Document] = {
    val activityOids: Seq[ObjectId] = phase.activity_ids[Many[ObjectId]]
    val activities: Seq[DynDoc] = BWMongoDB3.activities.
      find(Map("_id" -> Map("$in" -> activityOids), "bpmn_name" -> processName))
    val returnActivities = activities.map(activity => {
      val actions: Seq[DynDoc] = activity.actions[Many[Document]]
      val tasks = actions.map(action => {
        new Document("type", action.`type`[String]).append("name", action.name[String]).
          append("status", action.status[String]).append("duration", action.duration[String])
      })
      new Document("id", activity._id[ObjectId]).append("bpmn_id", activity.bpmn_id[String]).
        append("status", activity.status[String]).append("tasks", tasks).
        append("duration", getActivityDuration(activity))
    })
    returnActivities
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val bpmnFileName = parameters("bpmn_name").replaceAll(" ", "-")
      val phaseOid = new ObjectId(parameters("phase_id"))
      val processModelStream = getProcessModel(bpmnFileName)
      val blockBuffer = new Array[Byte](4096)
      val byteBuffer = mutable.Buffer.empty[Byte]
      def copyModelToOutput(): Unit = {
        val len = processModelStream.read(blockBuffer)
        if (len > 0) {
          byteBuffer.append(blockBuffer.take(len): _*)
          copyModelToOutput()
        }
      }
      copyModelToOutput()
      val xml = new String(byteBuffer.toArray)
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val processVariables = getVariables(phase, bpmnFileName)
      val processTimers = getTimers(phase, bpmnFileName)
      val processActivities = getActivities(phase, bpmnFileName)
      val returnValue = new Document("xml", xml).append("variables", processVariables).
        append("timers", processTimers).append("activities", processActivities)
      response.getWriter.println(bson2json(returnValue))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
