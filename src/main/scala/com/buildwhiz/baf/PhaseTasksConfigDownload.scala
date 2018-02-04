package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId
import org.bson.Document

class PhaseTasksConfigDownload extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    try {
      val projectOid = new ObjectId(parameters("project_id"))
      val phaseOid = new ObjectId(parameters("phase_id"))
      val bpmnName = parameters("bpmn_name")
      val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
      val projectName = project.name[String]
      val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
      val phaseName = phase.name[String]
      val activityIds: Seq[ObjectId] = phase.activity_ids[Many[ObjectId]]
      val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityIds),
          "bpmn_name" -> bpmnName))
      val outputStream = response.getOutputStream
      outputStream.println("Task, Type, Parent, Duration, Assignee, Description")
      for (activity <- activities) {
        val activityName = activity.name[String]
        val actions: Seq[DynDoc] = activity.actions[Many[Document]]
        for (task <- actions) {
          val taskName = task.name[String]
          val duration = task.duration[String]
          //val description = activity.description[String]
          val assignee = task.assignee_person_id[ObjectId]
          val aType = task.`type`[String]
          val buffer = s"$taskName ($projectName/$phaseName), $aType, $activityName, $duration, $assignee, ..."
          outputStream.println(buffer)
        }
      }
      response.setContentType("text/csv")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", s"EXIT-OK (${activities.length} tasks)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
