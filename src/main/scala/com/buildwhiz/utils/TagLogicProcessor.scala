package com.buildwhiz.utils

import com.buildwhiz.baf2.PhaseApi

import scala.util.parsing.combinator.RegexParsers

object TagLogicProcessor extends RegexParsers {

  type TestSet = Set[String] => Boolean

  private val AND: Parser[String] = "(?i)AND(?![A-Z0-9._-])".r
  private val OR: Parser[String] = "(?i)OR(?![A-Z0-9._-])".r
  private val NOT: Parser[String] = "(?i)NOT(?![A-Z0-9._-])".r

  private val inPhase: Parser[TestSet] = "(?i)@phase".r ~> "(" ~> "[^) ]+".r <~ ")" ^^
    { phaseName => (set: Set[String]) => set.contains(PhaseApi.phaseDocumentTagName(phaseName.toLowerCase)) }

  private val inTask: Parser[TestSet] = "(?i)@task".r ~> "(" ~> "[^) ]+".r <~ ")" ^^
    { phaseName => (set: Set[String]) => set.contains(s"@task(${phaseName.toLowerCase})") }

  private val LABEL: Parser[TestSet] = guard(not(AND | OR | NOT)) ~> "(?i)[A-Z](?:[A-Z0-9._-]*[A-Z0-9])?".r ^^
    { lbl => (set: Set[String]) => set.contains(lbl.toLowerCase) }

  private lazy val topExpr: Parser[TestSet] = rep1sep(andExpr, OR) ^^
    { exprList => (set: Set[String]) => exprList.map(exp => exp(set)).reduce((a, b) => a || b) }

  private lazy val andExpr: Parser[TestSet] = rep1sep(elementExpr, AND) ^^
    { exprList => (set: Set[String]) => exprList.map(exp => exp(set)).reduce((a, b) => a && b) }

  private lazy val elementExpr: Parser[TestSet] = "(" ~> topExpr <~ ")" | not | LABEL | inPhase | inTask

  private lazy val not: Parser[TestSet] = NOT ~> topExpr ^^ { expr => (set: Set[String]) => !expr(set) }

  def labelIsValid(label: String): Boolean = parseAll(LABEL, label).successful

  def logicIsValid(logic: String): Boolean = parse(logic).successful

  private def parse(str: String): ParseResult[TestSet] = parseAll(topExpr, str)

  def evaluateTagLogic(expr: String, set: Set[String]): Boolean = {
    parse(expr) match {
      case Success(test, _) => test(set.map(_.toLowerCase))
      case _ => false
    }
  }

  def main(args: Array[String]): Unit = {
    val status = logicIsValid("(alpha or beta) and not (@phase(planning) or @task(energy-optimization))")
    print(status)
  }

}


