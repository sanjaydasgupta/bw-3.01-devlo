package com.buildwhiz.baf

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.util.parsing.combinator.RegexParsers

class DocumentUserLabelLogicSet extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val labelName: String = parameters("label_name")
      val logic: String = parameters("logic").trim
      val parseResult = DocumentUserLabelLogicSet.parse(logic)
      if (logic.nonEmpty && !parseResult.successful)
        throw new IllegalArgumentException(s"bad logic at '${parseResult.next.source}'")
      val user: DynDoc = getUser(request)
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      val userLabels: Seq[DynDoc] = if (freshUserRecord.has("labels"))
        freshUserRecord.labels[Many[Document]] else Seq.empty[Document]
      val idx = userLabels.indexWhere(_.name[String] == labelName)
      if (idx == -1)
        throw new IllegalArgumentException(s"label '$labelName' not found")
      val documentIds: Seq[ObjectId] = userLabels(idx).document_ids[Many[ObjectId]]
      if (documentIds.nonEmpty)
        throw new IllegalArgumentException(s"label '$labelName' is already set manually")
      val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> user._id[ObjectId]),
        Map("$set" -> Map(s"labels.$idx.logic" -> logic)))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.audit(getClass.getName, request.getMethod, s"Added logic '$logic' to label '$labelName'", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        throw t
    }
  }

}

object DocumentUserLabelLogicSet extends RegexParsers {

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

  def parse(str: String): ParseResult[TestSet] = parseAll(topExpr, str)

  def eval(expr: String, set: Set[String]): Boolean = {
    parse(expr) match {
      case Success(test, _) => test(set)
      case _ => false
    }
  }

}
