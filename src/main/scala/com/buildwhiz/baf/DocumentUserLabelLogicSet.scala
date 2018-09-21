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
      val logic: String = parameters("logic")
      val logicOk = DocumentUserLabelLogicSet.parse(logic).successful
      if (!logicOk)
        throw new IllegalArgumentException(s"illegal logic expression '$logic'")
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

object DocumentUserLabelLogicSet extends RegexParsers {

  type TestSet = Set[String] => Boolean

  def label: Parser[TestSet] = "(?i)[A-Z](?:[A-Z0-9-]*[A-Z0-9])?".r ^^
    { lbl => set => set.contains(lbl) }

  def or: Parser[TestSet] = (label <~ "(?i)OR".r) ~ expression ^^
    { case lbl ~ expr => set => lbl(set) || expr(set) }

  def and: Parser[TestSet] = (label <~ "(?i)AND".r) ~ expression ^^
    { case lbl ~ expr => set => lbl(set) && expr(set) }

  def expression: Parser[TestSet] = and | or | "(" ~> expression <~ ")" | label

  def parse(str: String): ParseResult[TestSet] = parseAll(expression, str)

  def eval(expr: String, set: Set[String]): Boolean = {
    parse(expr) match {
      case Success(test, _) => test(set)
      case NoSuccess(_, _) => false
    }
  }

}
