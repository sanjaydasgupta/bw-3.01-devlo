package com.buildwhiz.baf2

import java.io.{File, FileOutputStream, InputStream}

import com.buildwhiz.infra.{BWMongoDB3, DynDoc, GoogleDrive}
import BWMongoDB3._
import DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.HttpServletRequest
import org.bson.Document
import org.bson.types.ObjectId

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import com.buildwhiz.utils.TagLogicProcessor

object DocumentApi extends HttpUtils {

  def documentById(documentOid: ObjectId): DynDoc = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).head

  def exists(documentOid: ObjectId): Boolean = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).nonEmpty

  def versions(doc: DynDoc): Seq[DynDoc] = {
    val allVersions: Seq[DynDoc] =
      if (doc.has("versions")) doc.versions[Many[Document]] else Seq.empty[DynDoc]
    val requiredFields = Seq("author_person_id", "file_name", "timestamp")
    allVersions.filter(version => requiredFields.forall(field => version.has(field)))
  }

  def createProjectDocumentRecord(name: String, description: String, fileType: String, systemLabels: Seq[String],
      projectOid: ObjectId, optPhaseOid: Option[ObjectId] = None, optAction: Option[(ObjectId, String)] = None,
      optCategory: Option[String] = None): ObjectId = {

    val optPhaseOid2: Option[ObjectId] = (optPhaseOid, optAction) match {
      case (optPhOid: Some[ObjectId], _) => optPhOid
      case (None, Some((pOid, _))) =>
        Some(ProcessApi.parentPhase(ActivityApi.parentProcess(pOid)._id[ObjectId])._id[ObjectId])
      case _ => None
    }

    val query = ((optPhaseOid2, optAction, optCategory) match {
      case (Some(phOid), Some((actOid, actName)), Some(category)) => Map("phase_id" -> phOid, "activity_id" -> actOid,
        "action_name" -> actName, "category" -> category)
      case (Some(phOid), Some((actOid, actName)), None) => Map("phase_id" -> phOid, "activity_id" -> actOid,
        "action_name" -> actName)

      case (Some(phOid), None, Some(category)) => Map("phase_id" -> phOid, "activity_id" -> Map("$exists" -> false),
        "action_name" -> Map("$exists" -> false), "category" -> category)
      case (Some(phOid), None, None) => Map("phase_id" -> phOid, "activity_id" -> Map("$exists" -> false),
        "action_name" -> Map("$exists" -> false))

      case (None, Some((activityOid, actionName)), Some(category)) => Map("phase_id" -> Map("$exists" -> false),
        "activity_id" -> activityOid, "action_name" -> actionName, "category" -> category)
      case (None, Some((activityOid, actionName)), None) => Map("phase_id" -> Map("$exists" -> false),
        "activity_id" -> activityOid, "action_name" -> actionName)

      case (None, None, Some(category)) => Map("phase_id" -> Map("$exists" -> false), "activity_id" -> Map("$exists" -> false),
        "action_name" -> Map("$exists" -> false), "category" -> category)
      case (None, None, None) => Map("phase_id" -> Map("$exists" -> false), "activity_id" -> Map("$exists" -> false),
        "action_name" -> Map("$exists" -> false))

      case _ => throw new IllegalArgumentException("Unsupported parameter combination")
    }) ++ Map("project_id" -> projectOid, "name" -> name)

    if (BWMongoDB3.document_master.find(query).asScala.nonEmpty)
      throw new IllegalArgumentException(s"File named '$name' already exists")

    val assertions = query.toSeq.filterNot(_._2.isInstanceOf[Map[_, _]]).toMap
    val newDocumentRecord = new Document(Map("description" -> description, "type" -> fileType,
      "labels" -> systemLabels, "timestamp" -> System.currentTimeMillis, "versions" -> Seq.empty[Document]) ++
      assertions)
    BWMongoDB3.document_master.insertOne(newDocumentRecord)
    newDocumentRecord.getObjectId("_id")
  }

  def usersDocumentList(user: DynDoc, optProjectOid: Option[ObjectId], request: HttpServletRequest): Seq[DynDoc] = {
    if (PersonApi.isBuildWhizAdmin(Right(user)) && optProjectOid.isDefined) {
      BWLogger.log(getClass.getName, "usersDocumentList", "Allowing full access to BW admin", request)
      BWMongoDB3.document_master.find(Map("project_id" -> optProjectOid.get))
    } else if (optProjectOid.map(pOid => ProjectApi.canManage(user._id[ObjectId], ProjectApi.projectById(pOid))).exists(b => b)) {
      BWLogger.log(getClass.getName, "usersDocumentList", "Allowing full access to project-manager", request)
      BWMongoDB3.document_master.find(Map("project_id" -> optProjectOid.get))
    } else if (optProjectOid.isDefined) {
      val projectOid = optProjectOid.get
      val assignments: Seq[DynDoc] = BWMongoDB3.activity_assignments.
          find(Map("project_id" -> projectOid, "person_id" -> user._id[ObjectId], "document_access" -> Map($exists -> true)))
      val accessValues: Seq[String] = assignments.flatMap(_.document_access[Many[String]]).distinct
      val theProject = ProjectApi.projectById(projectOid)
      val tagExpressionMap: Map[String, String] = theProject.document_tags[Many[Document]].map(tagSpec => {
        val name = tagSpec.name[String]
        if (tagSpec.has("logic")) {
          (name, s"(${tagSpec.logic[String]})")
        } else
          (name, name)
      }).toMap
      val accessExpression = accessValues.map(tagExpressionMap).distinct.mkString(" OR ")
      val allProjectDocs: Seq[DynDoc] = BWMongoDB3.document_master.find(Map("project_id" -> optProjectOid.get))
      val logMessage = s"""#Assignments: ${assignments.length}, Access-Values: ${accessValues.mkString(",")}, """ +
        s"""Expr-Map: $tagExpressionMap, Access-Expression: $accessExpression"""
      BWLogger.log(getClass.getName, "usersDocumentList", logMessage, request)
      val stringBuffer = new StringBuffer("Documents processed: ")
      val accessibleDocs = allProjectDocs.filter(doc => {
        val docTags: Seq[String] = if (doc.has("labels")) doc.labels[Many[String]] else Seq.empty[String]
        val accessible = TagLogicProcessor.evaluateTagLogic(accessExpression, docTags.toSet)
        stringBuffer.append(s"""[${doc.name[String]}: ${docTags.mkString(", ")} => $accessible], """)
        accessible
      })
      BWLogger.log(getClass.getName, "usersDocumentList", stringBuffer.toString, request)
      accessibleDocs
    } else {
      BWLogger.log(getClass.getName, "usersDocumentList", "User has NO access", request)
      Nil
    }
  }

  def documentList30(request: HttpServletRequest): Seq[DynDoc] = {
    val parameters = getParameterMap(request)
    val Seq(optProjectOid, optPhaseOid) = Seq("project_id", "phase_id").
        map(n => parameters.get(n).map(id => new ObjectId(id)))
    val user: DynDoc = getUser(request)
    val managed = optProjectOid.map(ProjectApi.projectById).map(ProjectApi.canManage(user._id[ObjectId], _)) match {
      case Some(true) => true
      case _ => false
    }
    if (PersonApi.isBuildWhizAdmin(Right(user)) || managed) {
      val query: Map[String, Any] = (optProjectOid, optPhaseOid) match {
        case (_, Some(phaseOid)) => Map("phase_id" -> phaseOid)
        case (Some(projectOid), _) => Map("project_id" -> projectOid)
        case _ => throw new IllegalArgumentException("No parameters found")
      }
      BWMongoDB3.document_master.find(query)
    } else {
      val myTeamOids: Seq[ObjectId] = PersonApi.allTeams30(user._id[ObjectId]).map(_._id[ObjectId])
      if (myTeamOids.nonEmpty) {
        val query: Map[String, Any] = (optProjectOid, optPhaseOid) match {
          case (_, Some(phaseOid)) =>
            Map("phase_id" -> phaseOid, "team_id" -> Map($in -> myTeamOids))
          case (Some(projectOid), _) =>
            Map("project_id" -> projectOid, "team_id" -> Map($in -> myTeamOids))
          case _ => throw new IllegalArgumentException("No parameters found")
        }
        BWMongoDB3.document_master.find(query)
      } else {
        Seq.empty[DynDoc]
      }
    }
  }

  def documentList3(request: HttpServletRequest): Seq[DynDoc] = {
    val parameters = getParameterMap(request)
    val parameterValues: Array[Option[ObjectId]] = Array("deliverable_id", "activity_id", "phase_id", "project_id").
        map(n => parameters.get(n).map(id => new ObjectId(id)))
    val query: Map[String, Any] = parameterValues match {
      case Array(Some(deliverableOid), _, _, _) => Map("deliverable_id" -> deliverableOid)
      case Array(None, Some(activityOid), _, _) => Map("activity_id" -> activityOid)
      case Array(None, None, Some(phaseOid), _) => Map("phase_id" -> phaseOid)
      case Array(None, None, None, Some(projectOid)) => Map("project_id" -> projectOid)
      case _ => throw new IllegalArgumentException("No parameters found")
    }
    BWMongoDB3.document_master.find(query)
  }

  def documentList(request: HttpServletRequest): Seq[DynDoc] = {
    val user: DynDoc = getUser(request)
    val isPrabhasAdmin = PersonApi.fullName(user) == "Prabhas Admin"
    val parameters = getParameterMap(request)
    val parameterValues = Array("action_name", "activity_id", "process_id", "phase_id", "project_id").
        map(n => parameters.get(n))
    def oid(id: String): ObjectId = new ObjectId(id)
    val privateDocumentIndicator = Map("labels" -> Map($not -> Map($in -> Seq("Contract", "Invoice"))))
    val allDocuments: Seq[DynDoc] = parameterValues match {
//      case Array(Some(actionName), Some(activityId), _, _, _) =>
//        BWMongoDB3.document_master.find(Map("activity_id" -> oid(activityId), "action_name" -> actionName))
      case Array(_, Some(activityId), _, _, _) if ActivityApi.exists(oid(activityId)) =>
        val activitySelector = Map($or -> Seq("activity_id" -> oid(activityId), "activity_ids" -> oid(activityId)))
        val selector: Map[String, Any] = if (isPrabhasAdmin)
          activitySelector
        else
          Map($and -> Seq(privateDocumentIndicator, activitySelector))
        BWMongoDB3.document_master.find(selector)
//      case Array(None, None, Some(processId), _, _) =>
//        BWMongoDB3.document_master.find(Map("process_id" -> oid(processId), "activity_id" -> Map("$exists" -> false)))
//      case Array(None, None, None, Some(phaseId), _) =>
//        BWMongoDB3.document_master.find(Map("phase_id" -> oid(phaseId), "process_id" -> Map("$exists" -> false),
//          "activity_id" -> Map("$exists" -> false)))
//      case Array(_, _, _, Some(phaseId), _) =>
//        if (isPrabhasAdmin)
//          BWMongoDB3.document_master.find(Map("phase_id" -> oid(phaseId)))
//        else
//          BWMongoDB3.document_master.find(Map($and -> Seq(Map("phase_id" -> oid(phaseId)), privateDocumentIndicator)))
      case Array(_, _, _, _, Some(projectId)) =>
        usersDocumentList(user, Some(projectId).map(id => new ObjectId(id)), request)
      case _ => Seq.empty[DynDoc]
    }
    allDocuments.filter(_.has("name"))
  }

  def getSystemTags(doc: DynDoc): Seq[String] = {

//    def fix(str: String) = str.replaceAll("\\s+", "-")

//    val legacyLabels = if (doc.has("category") && doc.has("subcategory")) {
//      Seq(fix(doc.category[String]), fix(doc.subcategory[String]))
//    } else {
//      Seq.empty[String]
//    }
//
//    It was decided not to display phase-tags, hence following comment outs ...
//    val phaseLabel = doc.get[ObjectId]("phase_id") match {
//      case Some(phaseOid) =>
//        val thePhase = PhaseApi.phaseById(phaseOid)
//        Seq(PhaseApi.phaseDocumentTagName(thePhase.name[String]))
//      case None => Seq.empty[String]
//    }
    val documentLabels: Seq[String] = if (doc.has("labels")) {
      doc.labels[Many[String]].map(_.trim).filter(_.nonEmpty)
    } else {
      Seq.empty[String]
    }

    //(/*phaseLabel ++*/ documentLabels/* ++ legacyLabels*/).distinct
    documentLabels.distinct
  }

  def docOid2UserTags(user: DynDoc): Map[ObjectId, Seq[String]] = {
    val userLabels: Seq[DynDoc] = if (user.has("labels")) user.labels[Many[Document]] else Seq.empty[DynDoc]
    userLabels.filter(label => {!label.has("logic") || label.logic[String].trim.isEmpty}).
      flatMap(label => {
        val labelName = label.name[String]
        val docOids: Seq[ObjectId] = label.document_ids[Many[ObjectId]]
        docOids.map(oid => (oid, labelName))
      }).groupBy(_._1).map(t => (t._1, t._2.map(_._2)))
  }

  def getLogicalTags(nonLogicalLabels: Seq[String], user: DynDoc): Seq[String] = {
    val userLabels: Seq[DynDoc] = if (user.has("labels"))
      user.labels[Many[Document]]
    else
      Seq.empty[DynDoc]
    val logicalLabels = userLabels.filter(label => label.has("logic") && label.logic[String].trim.nonEmpty).
      filter(label => TagLogicProcessor.evaluateTagLogic(label.logic[String], nonLogicalLabels.toSet))
    logicalLabels.map(_.name[String])
  }

  def storeDocument(fileName: String, is: InputStream, projectId: String, documentOid: ObjectId, timestamp: Long,
      comments: String, authorOid: ObjectId, properties: Map[String, String],
      request: HttpServletRequest): (String, Long) = {
    BWLogger.log(getClass.getName, "storeDocument", "ENTRY", request)
    //val s3key = f"$projectId-$documentOid-$timestamp%x"
    val storageKey = f"$projectId-$documentOid-$timestamp%x"
    //BWLogger.log(getClass.getName, "storeDocument", s"amazonS3Key: $s3key", request)
    BWLogger.log(getClass.getName, "storeDocument", s"storage-key: $storageKey", request)
    //val file = new File(s3key)
    val file = new File(storageKey)
    var fileLength = 0L
    try {
      val outFile = new FileOutputStream(file)
      val buffer = new Array[Byte](4096)
      @tailrec def handleBlock(length: Int = 0): Int = {
        val bytesRead = is.read(buffer)
        if (bytesRead > 0) {
          outFile.write(buffer, 0, bytesRead)
          handleBlock(length + bytesRead)
        } else {
          outFile.close()
          length
        }
      }
      fileLength = handleBlock()
      //AmazonS3.putObject(s3key, file)
      GoogleDrive.putObject(storageKey, file, properties)
      val versionRecord = Map("comments" -> comments, "timestamp" -> timestamp, "author_person_id" -> authorOid,
        "file_name" -> fileName, "size" -> fileLength)
      val updateResult = BWMongoDB3.document_master.
        updateOne(Map("_id" -> documentOid), Map("$push" -> Map("versions" -> versionRecord)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      BWLogger.log(getClass.getName, "storeDocument", s"EXIT-OK ($fileLength)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "storeDocument", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
    try {file.delete()} catch {case _: Throwable => /* No recovery */}
    //(s3key, fileLength)
    (storageKey, fileLength)
  }

}
