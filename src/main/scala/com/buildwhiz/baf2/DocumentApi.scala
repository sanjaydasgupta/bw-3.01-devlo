package com.buildwhiz.baf2

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import BWMongoDB3._
import DynDoc._
import org.bson.types.ObjectId

import scala.util.parsing.combinator.RegexParsers

object DocumentApi {

  def documentById(documentOid: ObjectId): DynDoc = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).head

  def exists(documentOid: ObjectId): Boolean = BWMongoDB3.document_master.find(Map("_id" -> documentOid)).nonEmpty

  object logic extends RegexParsers {

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
