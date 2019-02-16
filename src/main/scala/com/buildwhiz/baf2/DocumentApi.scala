package com.buildwhiz.baf2

import java.io.{File, FileOutputStream, InputStream}

import com.buildwhiz.infra.{AmazonS3, BWMongoDB3, DynDoc}
import BWMongoDB3._
import DynDoc._
import com.buildwhiz.utils.BWLogger
import javax.servlet.http.HttpServletRequest
import org.bson.types.ObjectId

import scala.annotation.tailrec
import scala.util.parsing.combinator.RegexParsers

object DocumentApi {

  def documentById(documentOid: ObjectId): DynDoc = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).head

  def exists(documentOid: ObjectId): Boolean = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).nonEmpty

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

  object tagLogic extends RegexParsers {

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

    def parse(str: String): ParseResult[TestSet] = parseAll(topExpr, str)

    def eval(expr: String, set: Set[String]): Boolean = {
      parse(expr) match {
        case Success(test, _) => test(set)
        case _ => false
      }
    }

  }

}
