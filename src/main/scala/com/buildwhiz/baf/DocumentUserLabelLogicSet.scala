package com.buildwhiz.baf

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.util.parsing.combinator.{PackratParsers, RegexParsers}

class DocumentUserLabelLogicSet extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val labelName: String = parameters("label_name")
      val logic: String = parameters("logic")
      val parseResult = DocumentUserLabelLogicSet.parse(logic)
      if (!parseResult.successful)
        throw new IllegalArgumentException(s"bad logic at '${parseResult.next.source}'")
      val user: DynDoc = getUser(request)
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
      val userLabels: Seq[DynDoc] = if (freshUserRecord.has("labels"))
        freshUserRecord.labels[Many[Document]] else Seq.empty[Document]
      val idx = userLabels.indexWhere(_.name[String] == labelName)
      if (idx == -1)
        throw new IllegalArgumentException(s"label '$labelName' not found")
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

object DocumentUserLabelLogicSet extends RegexParsers with PackratParsers {

  type TestSet = Set[String] => Boolean

  val AND: Parser[String] = "(?i)AND(?![A-Z0-9._-])".r
  val OR: Parser[String] = "(?i)OR(?![A-Z0-9._-])".r
  val NOT: Parser[String] = "(?i)NOT(?![A-Z0-9._-])".r

  val LABEL: Parser[TestSet] = guard(not(AND | OR | NOT)) ~> "(?i)[A-Z](?:[A-Z0-9._-]*[A-Z0-9])?".r ^^
    { lbl => (set: Set[String]) => set.contains(lbl) }

  val or: PackratParser[TestSet] = (expression <~ OR) ~ expression ^^
    { case expr1 ~ expr2 => (set: Set[String]) => expr1(set) || expr2(set) }
  val and: PackratParser[TestSet] = (expression <~ AND) ~ expression ^^
    { case expr1 ~ expr2 => (set: Set[String]) => expr1(set) && expr2(set) }
  val not: PackratParser[TestSet] = NOT ~> expression ^^ { expr => (set: Set[String]) => !expr(set) }

  lazy val expression: PackratParser[TestSet] = and | or | "(" ~> expression <~ ")" | not | LABEL

  def parse(str: String): ParseResult[TestSet] = parseAll(expression, str)

  def eval(stringToParse: String, labels: Set[String]): Boolean = {
    parse(stringToParse) match {
      case Success(expr, _) => expr(labels)
      case NoSuccess(_, _) => false
    }
  }

}
