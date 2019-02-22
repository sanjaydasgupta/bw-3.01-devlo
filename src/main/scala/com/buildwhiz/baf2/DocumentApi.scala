package com.buildwhiz.baf2

import java.io.{File, FileOutputStream, InputStream}

import com.buildwhiz.infra.{AmazonS3, BWMongoDB3, DynDoc}
import BWMongoDB3._
import DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.HttpServletRequest
import org.bson.Document
import org.bson.types.ObjectId

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.parsing.combinator.RegexParsers

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
      projectOid: ObjectId, phaseOid: Option[ObjectId] = None, action: Option[(ObjectId, String)] = None,
      optCategory: Option[String] = None): ObjectId = {

    val query = ((phaseOid, action, optCategory) match {
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

  def documentsByProjectId(request: HttpServletRequest): Seq[DynDoc] = {
    //val user: DynDoc = getUser(request)
    val parameters = getParameterMap(request)
    val parameterValues = Array("action_name", "activity_id", "process_id", "phase_id", "project_id").
        map(n => parameters.get(n))
    def oid(id: String): ObjectId = new ObjectId(id)

    val allDocuments: Seq[DynDoc] = parameterValues match {
      case Array(Some(actionName), Some(activityId), _, _, _) =>
        BWMongoDB3.document_master.find(Map("activity_id" -> oid(activityId), "action_name" -> actionName))
      case Array(None, Some(activityId), _, _, _) =>
        BWMongoDB3.document_master.find(Map("activity_id" -> oid(activityId), "action_name" -> Map("$exists" -> false)))
      case Array(None, None, Some(processId), _, _) =>
        BWMongoDB3.document_master.find(Map("process_id" -> oid(processId), "activity_id" -> Map("$exists" -> false)))
      case Array(None, None, None, Some(phaseId), _) =>
        BWMongoDB3.document_master.find(Map("phase_id" -> oid(phaseId), "process_id" -> Map("$exists" -> false),
          "activity_id" -> Map("$exists" -> false)))
      case Array(None, None, None, None, Some(projectId)) =>
        BWMongoDB3.document_master.find(Map("project_id" -> oid(projectId), "phase_id" -> Map("$exists" -> false),
          "process_id" -> Map("$exists" -> false), "activity_id" -> Map("$exists" -> false)))
      case _ => Seq.empty[DynDoc]
    }
    allDocuments.filter(_.has("name"))
  }

  def getSystemTags(doc: DynDoc): Seq[String] = {
    def fix(str: String) = str.replaceAll("\\s+", "-")
    val legacyLabels = if (doc.has("category") && doc.has("subcategory")) {
      Seq(fix(doc.category[String]), fix(doc.subcategory[String]))
    } else {
      Seq.empty[String]
    }
    val documentLabels: Seq[String] = if (doc.has("labels")) doc.labels[Many[String]] else Seq.empty[String]
    (documentLabels ++ legacyLabels).distinct
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
      filter(label => evaluateTagLogic(label.logic[String], nonLogicalLabels.toSet))
    logicalLabels.map(_.name[String])
  }

  def storeAmazonS3(fileName: String, is: InputStream, projectId: String, documentOid: ObjectId, timestamp: Long,
      comments: String, authorOid: ObjectId, request: HttpServletRequest): (String, Long) = {
    BWLogger.log(getClass.getName, "storeAmazonS3", "ENTRY", request)
    val s3key = f"$projectId-$documentOid-$timestamp%x"
    BWLogger.log(getClass.getName, "storeAmazonS3", s"amazonS3Key: $s3key", request)
    val file = new File(s3key)
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
      AmazonS3.putObject(s3key, file)
      val versionRecord = Map("comments" -> comments, "timestamp" -> timestamp, "author_person_id" -> authorOid,
        "file_name" -> fileName, "size" -> fileLength)
      val updateResult = BWMongoDB3.document_master.
        updateOne(Map("_id" -> documentOid), Map("$push" -> Map("versions" -> versionRecord)))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      BWLogger.log(getClass.getName, s"storeAmazonS3 ($fileLength)", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "storeAmazonS3", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
    try {file.delete()} catch {case _: Throwable => /* No recovery */}
    (s3key, fileLength)
  }

  def labelIsValid(label: String): Boolean = tagLogicParser.labelIsValid(label)
  def logicIsValid(logic: String): Boolean = tagLogicParser.logicIsValid(logic)
  def evaluateTagLogic(expr: String, set: Set[String]): Boolean = tagLogicParser.evaluateTagLogic(expr, set)

  private object tagLogicParser extends RegexParsers {

    type TestSet = Set[String] => Boolean

    val AND: Parser[String] = "(?i)AND(?![A-Z0-9._-])".r
    val OR: Parser[String] = "(?i)OR(?![A-Z0-9._-])".r
    val NOT: Parser[String] = "(?i)NOT(?![A-Z0-9._-])".r

    val LABEL: Parser[TestSet] = guard(not(AND | OR | NOT)) ~> "(?i)[A-Z](?:[A-Z0-9._-]*[A-Z0-9])?".r ^^
      { lbl => (set: Set[String]) => set.contains(lbl) }

    val topExpr: Parser[TestSet] = rep1sep(andExpr, OR) ^^
      { exprList => (set: Set[String]) => exprList.map(exp => exp(set)).reduce((a, b) => a || b) }

    lazy val andExpr: Parser[TestSet] = rep1sep(elementExpr, AND) ^^
      { exprList => (set: Set[String]) => exprList.map(exp => exp(set)).reduce((a, b) => a && b) }

    lazy val elementExpr: Parser[TestSet] = "(" ~> topExpr <~ ")" | not | LABEL

    lazy val not: Parser[TestSet] = NOT ~> topExpr ^^ { expr => (set: Set[String]) => !expr(set) }

    def labelIsValid(label: String): Boolean = parseAll(LABEL, label).successful

    def logicIsValid(logic: String): Boolean = parse(logic).successful

    private def parse(str: String): ParseResult[TestSet] = parseAll(topExpr, str)

    def evaluateTagLogic(expr: String, set: Set[String]): Boolean = {
      parse(expr) match {
        case Success(test, _) => test(set)
        case _ => false
      }
    }

  }

}
