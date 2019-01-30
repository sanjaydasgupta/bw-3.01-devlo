package com.buildwhiz.baf2

import com.buildwhiz.baf.DocumentUserLabelLogicSet
import com.buildwhiz.dot.GetDocumentVersionsList
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class DocumentList extends HttpServlet with HttpUtils with DateTimeUtils {

  private def getDocuments(user: DynDoc, request: HttpServletRequest): Seq[Document] = {
    val docOid2labels: Map[ObjectId, Seq[String]] = DocumentList.docOid2UserLabels(user)
    val docRecords: Seq[DynDoc] = allDocuments(request)
    val docProperties: Seq[Document] = docRecords.map(d => {
      val versions: Seq[DynDoc] = GetDocumentVersionsList.versions(d)
      val systemLabels = DocumentList.getSystemLabels(d)
      val userLabels = docOid2labels.getOrElse(d._id[ObjectId], Seq.empty[String])
      val logicalLabels = DocumentList.getLogicalLabels(systemLabels ++ userLabels, user)
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

  private def allDocuments(request: HttpServletRequest): Seq[DynDoc] = {

    def string2objectId(str: String) = new ObjectId(str)

    val parameterInfo: Seq[(String, String => AnyRef)] = Seq(
      ("project_id", string2objectId),
      ("phase_id", string2objectId),
      ("process_id", string2objectId),
      ("activity_id", string2objectId),
      ("action_name", a => a)
    )

    val parameters = getParameterMap(request)

    val mongoQuery: Map[String, AnyRef] = parameterInfo.map(pair => (pair._1, parameters.get(pair._1), pair._2)).
      filter(_._2.nonEmpty).map(triple => (triple._1, triple._3(triple._2.get))).toMap

    val liveProjects: Seq[DynDoc] = BWMongoDB3.projects.find()
    val projectOids: Seq[ObjectId] = liveProjects.map(_._id[ObjectId])
    val qualifier = Map("project_id" -> Map("$in" -> projectOids), "name" -> Map("$exists" -> true))

    BWMongoDB3.document_master.find(qualifier ++ mongoQuery)
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    //val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      val allDocuments = getDocuments(freshUserRecord, request)
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

object DocumentList {

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
