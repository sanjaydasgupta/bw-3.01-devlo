package com.buildwhiz.dot

import com.buildwhiz.api.{Activity, Action, Phase, Project}
import com.buildwhiz.baf.DocumentUserLabelLogicSet
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class GetDocumentsSummary extends HttpServlet with HttpUtils with DateTimeUtils {

  private def documentsByActivity(activityOid: ObjectId, optUser: Option[DynDoc]): Seq[DynDoc] = {
    val documents: Seq[DynDoc] = optUser match {
      case None => BWMongoDB3.document_master.find(Map("activity_id" -> activityOid))
      case Some(user) =>
        val actions: Seq[DynDoc] = Activity.allActions(activityOid).
            filter(action => Action.actionUsers(action).contains(user._id[ObjectId]))
        val actionNames = actions.map(_.name[String])
        BWMongoDB3.document_master.find(Map("activity_id" -> activityOid,
            "action_name" -> Map("$in" -> actionNames)))
    }
    documents
  }

  private def documentsByPhase(user: DynDoc, phaseOid: ObjectId): Seq[DynDoc] = {
    val phase: DynDoc = BWMongoDB3.phases.find(Map("_id" -> phaseOid)).head
    val userHasPhaseRole = Phase.phaseLevelUsers(phase).contains(user._id[ObjectId])
    val activityOids = Phase.allActivityOids(phase)
    val documents: Seq[DynDoc] = if (userHasPhaseRole) {
      activityOids.flatMap(activityOid => documentsByActivity(activityOid, None))
    } else {
      activityOids.flatMap(activityOid => documentsByActivity(activityOid, Some(user)))
    }
    documents
  }

  private def documentsByProject(user: DynDoc, projectOid: ObjectId): Seq[DynDoc] = {
    val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> projectOid)).head
    val userHasProjectRole = Project.projectLevelUsers(project).contains(user._id[ObjectId])
    val documents: Seq[DynDoc] = if (userHasProjectRole) {
      BWMongoDB3.document_master.find(Map("project_id" -> projectOid, "name" -> Map("$exists" -> true)))
    } else {
      val phaseOids = Project.allPhaseOids(project)
      phaseOids.flatMap(phaseOid => documentsByPhase(user, phaseOid))
    }
    documents
  }

  private def findDocuments(user: DynDoc):
      Seq[DynDoc] = {
    val projectOids: Seq[ObjectId] = user.project_ids[Many[ObjectId]]
    val projects: Seq[DynDoc] = BWMongoDB3.projects.find(Map("_id" -> Map("$in" -> projectOids)))

    val (managedProjects, nonManagedProjects) = projects.partition(_.admin_person_id[ObjectId] == user._id[ObjectId])

    val managedProjectIds: Seq[ObjectId] = managedProjects.map(_._id[ObjectId])
    val docsInManagedProjects: Seq[DynDoc] = BWMongoDB3.document_master.
        find(Map("project_id" -> Map("$in" -> managedProjectIds), "name" -> Map("$exists" -> true)))

    val idsOfPhasesInNonManagedProjects: Seq[ObjectId] = nonManagedProjects.flatMap(_.phase_ids[Many[ObjectId]])
    val phasesInNonManagedProjects: Seq[DynDoc] = BWMongoDB3.phases.
        find(Map("_id" -> Map("$in" -> idsOfPhasesInNonManagedProjects)))
    val (managedPhases, nonManagedPhases) = phasesInNonManagedProjects.
        partition(_.admin_person_id[ObjectId] == user._id[ObjectId])
    val activityIdsInManagedPhases: Seq[ObjectId] = managedPhases.flatMap(_.activity_ids[Many[ObjectId]])
    val docsInManagedPhases: Seq[DynDoc] = BWMongoDB3.document_master.
      find(Map("activity_id" -> Map("$in" -> activityIdsInManagedPhases), "name" -> Map("$exists" -> true)))

    val idsOfActivitiesInNonManagedPhases = nonManagedPhases.flatMap(_.activity_ids[Many[ObjectId]])
    val activitiesInNonManagedProjects: Seq[DynDoc] = BWMongoDB3.activities.
      find(Map("_id" -> Map("$in" -> idsOfActivitiesInNonManagedPhases)))
    val activityActionPairs: Seq[(DynDoc, DynDoc)] = activitiesInNonManagedProjects.
        flatMap(activity => activity.actions[Many[Document]].map(action => (activity, action)))
    val assignedActivityActionPairs = activityActionPairs.filter(_._2.assignee_person_id[ObjectId] == user._id[ObjectId])
    val assignedDocuments: Seq[DynDoc] = assignedActivityActionPairs.flatMap(aa => {
      val activityOid = aa._1._id[ObjectId]
      val actionName = aa._2.name[String]
      BWMongoDB3.document_master.find(Map("activity_id" -> activityOid, "action_name" -> actionName,
        "name" -> Map("$exists" -> true)))
    })

    val authoredDocs: Seq[DynDoc] = BWMongoDB3.document_master.find(Map("project_id" -> Map("$in" -> projectOids),
        "versions.author_person_id" -> user._id[ObjectId], "name" -> Map("$exists" -> true)))

    (docsInManagedProjects ++ docsInManagedPhases ++ assignedDocuments ++ authoredDocs).distinct.
      filterNot(d => d.has("category") && d.category[String] == "SYSTEM")
  }

  private def getDocuments(user: DynDoc, optProjectOid: Option[ObjectId], optPhaseOid: Option[ObjectId]):
      Seq[Document] = {
    val docOid2labels: Map[ObjectId, Seq[String]] = GetDocumentsSummary.docOid2UserLabels(user)
    val docRecords: Seq[DynDoc] = (optProjectOid, optPhaseOid) match {
      case (_, Some(phaseOid)) => documentsByPhase(user, phaseOid)
      case (Some(projectOid), _) => documentsByProject(user, projectOid)
      case _ => findDocuments(user)
    }
    val docProperties: Seq[Document] = docRecords.map(d => {
      val versions: Seq[DynDoc] = GetDocumentVersionsList.versions(d)
      val systemLabels = GetDocumentsSummary.getSystemLabels(d)
      val userLabels = docOid2labels.getOrElse(d._id[ObjectId], Seq.empty[String])
      val logicalLabels = GetDocumentsSummary.getLogicalLabels(systemLabels ++ userLabels, user)
      val allUserLabels = userLabels ++ logicalLabels
      val allLabelsCsv = (systemLabels ++ allUserLabels).mkString(",")
      val project: DynDoc = BWMongoDB3.projects.find(Map("_id" -> d.project_id[ObjectId])).head
      val hasVersions = versions.nonEmpty
      val documentProperties: Document = if (hasVersions) {
        val lastVersion: DynDoc = versions.sortWith(_.timestamp[Long] < _.timestamp[Long]).last
        val fileType = lastVersion.file_name[String].split("\\.").last
        val date = dateTimeString(lastVersion.timestamp[Long], Some(user.tz[String]))
        val authorOid = lastVersion.author_person_id[ObjectId]
        val author: DynDoc = BWMongoDB3.persons.find(Map("_id" -> authorOid)).head
        val authorName = s"${author.first_name[String]} ${author.last_name[String]}"
        Map("name" -> d.name[String], "_id" -> d._id[ObjectId].toString, "phase" -> "???",
          "labels" -> Map("system" -> systemLabels, "user" -> allUserLabels, "all_csv" -> allLabelsCsv),
          "type" -> fileType, "author" -> authorName, "date" -> date, "project_id" -> d.project_id[ObjectId].toString,
          "project_name" -> project.name[String], "timestamp" -> lastVersion.timestamp[Long],
          "has_versions" -> true)
      } else {
        Map("name" -> d.name[String], "_id" -> d._id[ObjectId].toString, "phase" -> "???",
          "labels" -> Map("system" -> systemLabels, "user" -> allUserLabels, "all_csv" -> allLabelsCsv),
          "type" -> "???", "author" -> "???", "date" -> "???", "project_id" -> d.project_id[ObjectId].toString,
          "project_name" -> project.name[String], "timestamp" -> 0L, "has_versions" -> false)
      }
      documentProperties
    })
    docProperties.groupBy(_.getString("_id")).toSeq.map(_._2.head)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      val projectOid: Option[ObjectId] = parameters.get("project_id").map(pid => new ObjectId(pid))
      val phaseOid: Option[ObjectId] = parameters.get("phase_id").map(pid => new ObjectId(pid))
      val allDocuments = getDocuments(freshUserRecord, projectOid, phaseOid)
      response.getWriter.print(allDocuments.map(document => bson2json(document)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${allDocuments.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}

object GetDocumentsSummary {

  def getSystemLabels(doc: DynDoc): Seq[String] = {
    def fix(str: String) = str.replaceAll("\\s+", "-")
    val labels1 = if (doc.has("category") && doc.has("subcategory")) {
      Seq(s"""${fix(doc.category[String])}.${fix(doc.subcategory[String])}""")
    } else {
      Seq.empty[String]
    }
    val labels: Seq[String] = if (doc.has("labels")) doc.labels[Many[String]] else Seq.empty[String]
    (labels ++ labels1).distinct
  }

  def docOid2UserLabels(user: DynDoc): Map[ObjectId, Seq[String]] = {
    val userLabels: Seq[DynDoc] = if (user.has("labels")) user.labels[Many[Document]] else Seq.empty[DynDoc]
    userLabels.filter(label => {!label.has("logic") || label.logic[String].trim.isEmpty}).
      flatMap(label => {
      val labelName = label.name[String]
      val docOids: Seq[ObjectId] = label.document_ids[Many[ObjectId]]
      docOids.map(oid => (oid, labelName))
    }).groupBy(_._1).map(t => (t._1, t._2.map(_._2)))
  }

  def getLogicalLabels(nonLogicalLabels: Seq[String], user: DynDoc): Seq[String] = {
    val userLabels: Seq[DynDoc] = if (user.has("labels"))
      user.labels[Many[Document]]
    else
      Seq.empty[DynDoc]
    val logicalLabels = userLabels.filter(label => label.has("logic") && label.logic[String].trim.nonEmpty).
        filter(label => DocumentUserLabelLogicSet.eval(label.logic[String], nonLogicalLabels.toSet))
    logicalLabels.map(_.name[String])
  }

}
