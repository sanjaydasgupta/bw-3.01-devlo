package com.buildwhiz.web

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.baf.ActionComplete
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import scala.jdk.CollectionConverters._
import scala.collection.mutable

class BrowseActions extends HttpServlet with HttpUtils {

  private def peopleNames(personOidOption: Option[ObjectId]): String = {

    def personStatusColor(person: DynDoc): String = {
      val projectIds = person.project_ids[Many[ObjectId]]
      if (projectIds.isEmpty) {
        "black"
      } else {
        val projects: Seq[DynDoc] = BWMongoDB3.projects.find(Map("_id" -> Map("$in" -> projectIds)))
        val phaseIds = projects.flatMap(_.process_ids[Many[ObjectId]])
        val phases: Seq[DynDoc] = BWMongoDB3.processes.find(Map("_id" -> Map("$in" -> phaseIds)))
        val activityIds = phases.flatMap(_.activity_ids[Many[ObjectId]])
        val activities: Seq[DynDoc] = BWMongoDB3.tasks.find(Map("_id" -> Map("$in" -> activityIds)))
        val actions: Seq[DynDoc] = activities.flatMap(_.actions[Many[Document]]).
          filter(_.assignee_person_id[ObjectId] == person._id[ObjectId])
        if (actions.exists(_.status[String] == "waiting"))
          "magenta"
        else if (actions.exists(_.status[String] == "defined"))
          "yellow"
        else
          "gray"
      }
    }

    val personOid = personOidOption.getOrElse(new ObjectId())
    val text = mutable.Buffer.empty[String]
    val persons: Seq[DynDoc] = BWMongoDB3.persons.find()
    text += "<table width=\"100%\">"
    for (person <- persons) {
      val fontWeight = if (personOid == person._id[ObjectId]) "bold" else "normal"
      val name = s"${person.first_name[String]}&nbsp;${person.last_name[String]}"
      text += s"""<tr><td style="font-weight: $fontWeight;">
           |<span style="background-color: ${personStatusColor(person)}; border: 1px solid black;">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>
           |<a href="BrowseActions?person_id=${person._id[ObjectId]}">$name</a></td></tr>""".stripMargin
    }
    text += "</table>"
    text.mkString("\n")
  }

  private def statusTable(personOid: ObjectId): String = {

    def actionStatusColor(action: DynDoc): String =
      (action.status[String], action.assignee_person_id[ObjectId] == personOid) match {
        case ("defined", _) => "yellow"
        case ("waiting", true) => "magenta"
        case ("waiting", false) => "brown"
        case ("ended", _) => "gray"
        case _ => "red"
    }

    val actionData = mutable.Buffer.empty[DynDoc]
    val text = mutable.Buffer.empty[String]
    val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
    val projectOids = person.project_ids[Many[ObjectId]]
    val projects: Seq[DynDoc] = BWMongoDB3.projects.find(Map("_id" -> Map("$in" -> projectOids)))
    for (project <- projects) {
      val phaseOids: Many[ObjectId] = project.process_ids[Many[ObjectId]]
      val phases: Seq[DynDoc] = BWMongoDB3.processes.find(Map("_id" -> Map("$in" -> phaseOids)))
      for (phase <- phases) {
        val activityOids: Many[ObjectId] = phase.activity_ids[Many[ObjectId]]
        val activities: Seq[DynDoc] = BWMongoDB3.tasks.find(Map("_id" -> Map("$in" -> activityOids)))
        val activitiesByBpmn = activities.groupBy(_.bpmn_name[String])
        for ((bpmnName, activities) <- activitiesByBpmn) {
          for (activity <- activities) {
            val actions: Seq[DynDoc] = activity.actions[Many[Document]]
            for (action <- actions.filter(_.assignee_person_id[ObjectId] == personOid)) {
              action.project_name = project.name[String]
              action.phase_name = phase.name[String]
              action.activity_name = activity.name[String]
              action.activity_id = activity._id[ObjectId]
              actionData += action
            }
          }
        }
      }
    }
    def sortKey(actions: Seq[DynDoc]): Int = actions.foldLeft(2)((k, act) => {
      (k, act.status[String]) match {
        case (0, _) => 0
        case (_, "waiting") => 0
        case (1, _) => 1
        case (_, "defined") => 1
        case _ => 2
      }
    })
    if (actionData.isEmpty) {
      text += s"""<span style="font-size: large; background-color: red; color: white;">No projects at this time</span>"""
    } else {
      val groupedActions = actionData.groupBy(a => (a.project_name[String], a.phase_name[String], a.bpmn_name[String]))
      text += "<table border=\"1\" width=\"100%\">"
      for ((group, theActions) <- groupedActions.toSeq.sortBy(p => sortKey(p._2.toSeq))) {
        text +=
          s"""<tr><td style="font-weight: bold; text-align: center; color: white; background-color: blue;" colspan="2">
              |${group._1}:${group._2}&nbsp;&nbsp;&nbsp;&nbsp;${group._3}</td></tr>""".stripMargin
        text +=
          """<tr style="text-align: center; color: white; background-color: blue;"><td align=\"center\">Action</td></tr>"""
        for (anAction <- theActions) {
          val activityLink = s"activity_id=${anAction.activity_id[ObjectId]}"
          val actionLink = s"action_name=${anAction.name[String]}"
          val displayName = if (anAction.`type`[String] == "main")
            anAction.activity_name[String] else s"${anAction.activity_name[String]}/${anAction.name[String]}"
          val statusColor = actionStatusColor(anAction)
          text += s"""<tr><td><span style="background-color: $statusColor; border: 1px solid black;">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>
            |<a href="BrowseActions?$activityLink&$actionLink&person_id=$personOid">$displayName</a>
            |</td></tr>""".stripMargin
        }
      }
      text += "</table>"
    }
    text.mkString("\n")
  }

  private def actionPanel(activityOid: ObjectId, actionName: String, personOid: ObjectId): String = {

    def docId2Document(project: DynDoc, docIds: Many[ObjectId], createdAfter: Long): Many[Document] = {
      val docs: Seq[DynDoc] = docIds.asScala.map(id =>BWMongoDB3.document_master.find(Map("_id" -> id)).head).toSeq
      for (doc <- docs) {
        val isReady = if (project has "documents") {
          project.documents[Many[Document]].exists(d => d.document_id[ObjectId] == doc._id[ObjectId] &&
            d.timestamp[Long] > createdAfter)
        } else {
          false
        }
        doc.is_ready = isReady
      }
      docs.map(_.asDoc)
    }.asJava

    val activity: DynDoc = BWMongoDB3.tasks.find(Map("_id" -> activityOid)).head
    val action: DynDoc = activity.actions[Many[Document]].filter(_.name[String] == actionName).head
    val text = mutable.Buffer.empty[String]
    text += "<table width=\"100%\" border=\"1\">" +
      s"""<tr><td style="text-align: center; color: white; background-color: blue; font-weight: bold; font-size: large;">
         |${action.name[String]} (${action.status[String]}) Duration: ${action.duration[String]}</td></tr>""".stripMargin
    if (action.assignee_person_id[ObjectId] == personOid) {
      val phase: DynDoc = BWMongoDB3.processes.find(Map("activity_ids" -> activityOid)).head
      val project: DynDoc = BWMongoDB3.projects.find(Map("phase_ids" -> phase._id[ObjectId])).head
      val isRelevant = action.assignee_person_id[ObjectId] == personOid
      if (isRelevant) {
        val p0 = if (project has "timestamps") project.timestamps[Document].y.start[Long] else Long.MaxValue
        val inDocuments = docId2Document(project, action.inbox[Many[ObjectId]], p0)
        text +=
          s"""<tr><td style="text-align: center; color: white; background-color: blue;">
              |Available Documents</td></tr>""".stripMargin
        if (inDocuments.isEmpty) {
          text += s"""<tr><td style="text-align:center;">None</td></tr>"""
        } else {
          for (inDoc <- inDocuments) {
            if (inDoc.is_ready[Boolean]) {
              val link = s"""DocumentDownload/${inDoc.name[String]}?project_id=${project._id}&document_id=${inDoc._id}"""
              text += s"""<tr><td><a href="$link" target="_blank">${inDoc.name[String]}</a></td></tr>"""
            } else {
              text += s"""<tr><td>${inDoc.name[String]}</td></tr>"""
            }
          }
        }
        val t0 = if (action has "timestamps") action.timestamps[Document].y.start[Long] else Long.MaxValue
        val outDocuments = docId2Document(project, action.outbox[Many[ObjectId]], t0)
        text +=
          s"""<tr><td style="text-align: center; color: white; background-color: blue;">
              |Required Documents</td></tr>""".stripMargin
        if (outDocuments.isEmpty) {
          text += s"""<tr><td style="text-align:center;">None</td></tr>"""
        } else {
          for (outDoc <- outDocuments) {

          }
        }
        val isReady = outDocuments.forall(_.is_ready[Boolean])
        val isReview = action.`type`[String] == "review"
        val isWaiting = action.status[String] == "waiting"
        text += s"""<tr><td style="text-align: center; color: white; background-color: blue;">""" +
          s"""Action Completion (${if (isReady && isWaiting) "Ready" else "Not Ready"})</td></tr>"""
        val activityLink = s"activity_id=${activity._id[ObjectId]}"
        val actionLink = s"action_name=${action.name[String]}"
        text +=
          s"""<tr><td style="text-align: center;">
              |${if (isReview) "<input type=\"checkbox\"/> Review OK&nbsp;&nbsp;&nbsp;&nbsp;" else ""}
              |<a href="BrowseActions?$activityLink&$actionLink&person_id=$personOid&complete=ok"><button
              |${if (isReady && isWaiting) "" else " disabled"}>Complete</button></a>
              |</td></tr>""".stripMargin
      }
    }
    text.mkString("\n")
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    if (parameters.contains("complete")) {
      ActionComplete.doPost(request, response)
      Thread.`yield`()
    }
    val writer = response.getWriter
    writer.println("<html><head><title>Actions</title></head><body>")
    try {
      val personOid: Option[ObjectId] = parameters.get("person_id").map(id => new ObjectId(id))
      val actionPanelText = (personOid, parameters.get("activity_id"), parameters.get("action_name")) match {
        case (Some(pOid), Some(activityId), Some(actionName)) => actionPanel(new ObjectId(activityId), actionName, pOid)
        case _ => ""
      }
      val statusTableText = personOid match {
        case Some(pOid) => statusTable(pOid)
        case None => ""
      }
      writer.println(
        s"""<table width="100%"><tr><td width="15%"> </td><td width="25%"> </td><td width="60%"> </td></tr>
           |<tr><td style="vertical-align: top;" width="15%">${peopleNames(personOid)}</td>
           |<td style="vertical-align: top;" width="25%">$statusTableText</td>
           |<td style="vertical-align: top;" width="60%">$actionPanelText</td></tr>""".stripMargin)
      writer.println("</table></body></html>")
      response.setContentType("text/html")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
