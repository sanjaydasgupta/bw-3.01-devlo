package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.HttpUtils
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._
import scala.collection.mutable

class OwnedActionsSummary extends HttpServlet with HttpUtils {

  private def copyParentReferences(action: DynDoc, project: DynDoc, phase: DynDoc, activity: DynDoc): Document = {
    val newDocument = new Document().y
    newDocument.name = action.name
    newDocument.status = action.status
    newDocument.project_name = project.name[String]
    newDocument.project_id = project._id[ObjectId]
    newDocument.phase_name = phase.name[String]
    newDocument.phase_id = phase._id[ObjectId]
    newDocument.activity_name = activity.name[String]
    newDocument.activity_id = activity._id[ObjectId]
    newDocument.activity_description = activity.description[String]
    newDocument.group_name = s"${project.name[String]}/${phase.name[String]}/${action.bpmn_name[String]}"
    newDocument.asDoc
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val filterKey = parameters("filter_key")
      val projectOids: ObjectIdList = BWMongoDB3.persons.find(Map("_id" -> personOid)).asScala.head.y.project_ids[ObjectIdList]
      val projects: Seq[DynDoc] = BWMongoDB3.projects.find(Map("_id" -> Map("$in" -> projectOids))).asScala.toSeq
      //val actionOrder = Map("prerequisite" -> 1, "main" -> 2, "review" -> 3)
      val allActions = mutable.Buffer.empty[DynDoc]
      for (project <- projects) {
        val phaseOids = project.phase_ids[ObjectIdList]
        val phases: Seq[DynDoc] = BWMongoDB3.phases.find(Map("_id" -> Map("$in" -> phaseOids))).asScala.toSeq
        for (phase <- phases) {
          val activityOids = phase.activity_ids[ObjectIdList]
          val activities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map("$in" -> activityOids))).asScala.toSeq
          for (activity <- activities) {
            val actions: Seq[DynDoc] = activity.actions[DocumentList]
            val relevantActions = actions.filter(action => action.assignee_person_id[ObjectId] == personOid)
            val filteredActions = relevantActions.filter(action => filterKey match {
              case "active" => action.status[String] == "waiting"
              case "all" => true
              case _ => true // placeholder, to be changed later
            })
            allActions ++= filteredActions.map(a => copyParentReferences(a, project, phase, activity))
          }
        }
      }
      writer.print(allActions.map(activity => bson2json(activity.asDoc)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

}