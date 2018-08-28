package com.buildwhiz.dot

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.mutable

class GetDocumentsSummary extends HttpServlet with HttpUtils with DateTimeUtils {

  private def getLabels(doc: Document): Seq[String] = {
    val labels1 = Seq("category", "subcategory", "keywords").
        map(doc.getOrDefault(_, "").asInstanceOf[String]).flatMap(_.split(",")).
        map(_.trim).filter(_.nonEmpty)
    val labels: Seq[String] = if (doc.has("labels")) doc.y.labels[Many[String]] else Seq.empty[String]
    (labels ++ labels1).distinct
  }

  private def findDocuments(user: DynDoc): Seq[DynDoc] = {
    val projectOids: Seq[ObjectId] = user.project_ids[Many[ObjectId]]
    val projects: Seq[DynDoc] = BWMongoDB3.projects.find(Map("_id" -> Map("$in" -> projectOids)))

    val (managedProjects, nonManagedProjects) = projects.partition(_.admin_person_id[ObjectId] == user._id[ObjectId])

    val managedProjectIds: Seq[ObjectId] = managedProjects.map(_._id[ObjectId])
    val docsInManagedProjects: Seq[DynDoc] = BWMongoDB3.document_master.
        find(Map("project_id" -> Map("$in" -> managedProjectIds)))

    val idsOfPhasesInNonManagedProjects: Seq[ObjectId] = nonManagedProjects.flatMap(_.phase_ids[Many[ObjectId]])
    val phasesInNonManagedProjects: Seq[DynDoc] = BWMongoDB3.phases.
        find(Map("_id" -> Map("$in" -> idsOfPhasesInNonManagedProjects)))
    val (managedPhases, nonManagedPhases) = phasesInNonManagedProjects.
        partition(_.admin_person_id[ObjectId] == user._id[ObjectId])
    val activityIdsInManagedPhases: Seq[ObjectId] = managedPhases.flatMap(_.activity_ids[Many[ObjectId]])
    val docsInManagedPhases: Seq[DynDoc] = BWMongoDB3.document_master.
      find(Map("activity_id" -> Map("$in" -> activityIdsInManagedPhases)))

    val idsOfActivitiesInNonManagedPhases = nonManagedPhases.flatMap(_.activity_ids[Many[ObjectId]])
    val activitiesInNonManagedProjects: Seq[DynDoc] = BWMongoDB3.activities.
      find(Map("_id" -> Map("$in" -> idsOfActivitiesInNonManagedPhases)))
    val activityActionPairs: Seq[(DynDoc, DynDoc)] = activitiesInNonManagedProjects.
        flatMap(activity => activity.actions[Many[Document]].map(action => (activity, action)))
    val assignedActivityActionPairs = activityActionPairs.filter(_._2.assignee_person_id[ObjectId] == user._id[ObjectId])
    val assignedDocuments: Seq[DynDoc] = assignedActivityActionPairs.flatMap(aa => {
      val activityOid = aa._1._id[ObjectId]
      val actionName = aa._2.name[String]
      BWMongoDB3.document_master.find(Map("activity_id" -> activityOid, "action_name" -> actionName))
    })

    docsInManagedProjects ++ docsInManagedPhases ++ assignedDocuments
  }

  private def getDocuments(user: DynDoc): Seq[Document] = {
    val userLabels: Seq[DynDoc] = if (user.has("labels")) user.labels[Many[Document]] else Seq.empty[DynDoc]
    val docOid2labels = mutable.Map.empty[ObjectId, mutable.Buffer[String]]
    for (label <- userLabels) {
      val labelName = label.name[String]
      val docOids: Seq[ObjectId] = label.document_ids[Many[ObjectId]]
      for (docOid <- docOids) {
        val docLabels: mutable.Buffer[String] = docOid2labels.getOrElse(docOid, mutable.Buffer.empty[String])
        docLabels.append(labelName)
        docOid2labels.put(docOid, docLabels)
      }
    }
    val docs: Seq[DynDoc] = findDocuments(user)
    val docProperties: Seq[Document] = docs.map(d => {
      val versions: Seq[DynDoc] = GetDocumentVersionsList.versions(d)
      val systemLabels = getLabels(d.asDoc)
      val userLabels = docOid2labels.getOrElse(d._id[ObjectId], Seq.empty[String])
      val allLabelsCsv = (systemLabels ++ userLabels).mkString(",")
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
          "labels" -> Map("system" -> systemLabels, "user" -> userLabels, "all_csv" -> allLabelsCsv),
          "type" -> fileType, "author" -> authorName, "date" -> date, "project_id" -> d.project_id[ObjectId].toString,
          "project_name" -> project.name[String], "timestamp" -> lastVersion.timestamp[Long],
          "has_versions" -> true)
      } else {
        Map("name" -> d.name[String], "_id" -> d._id[ObjectId].toString, "phase" -> "???",
          "labels" -> Map("system" -> systemLabels, "user" -> userLabels, "all_csv" -> allLabelsCsv),
          "type" -> "???", "author" -> "???", "date" -> "???", "project_id" -> d.project_id[ObjectId].toString,
          "project_name" -> project.name[String], "timestamp" -> 0L, "has_versions" -> false)
      }
      documentProperties
    })
    docProperties
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    //val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    try {
      val user: DynDoc = getUser(request)
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      val allDocuments = getDocuments(freshUserRecord)
      writer.print(allDocuments.map(document => bson2json(document)).mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK (${allDocuments.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}