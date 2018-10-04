package com.buildwhiz

//import com.buildwhiz.ScratchPad.labelParser
//import com.buildwhiz.baf.DocumentLabelAdd.isValidLabel
//import com.buildwhiz.baf.DocumentUserLabelLogicSet.TestSet
//import javax.mail.internet.InternetAddress
//import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
//import com.buildwhiz.infra.BWMongoDB3._
//import com.buildwhiz.infra.DynDoc._

//import scala.collection.JavaConverters._
//import org.bson.types.ObjectId
//import org.bson.Document
//import org.camunda.bpm.engine.ProcessEngines

//import scala.util.parsing.input.{Position, Reader}

//val docs: Seq[DynDoc] = BWMongoDB3.document_master.find().asScala.toSeq
//println(s"Total docs: ${docs.length}")
//val withVersions = docs.filter(_.has("versions"))
//println(s"With versions: ${withVersions.length}")
//val versions: Seq[DynDoc] = withVersions.flatMap(_.versions[DocumentList])
//println(s"All versions: ${versions.length}")
//val withRfis = versions.filter(_.has("rfi_ids"))
//println(s"With rfis: ${withRfis.length}")

//  val obj1 = new ObjectId("56f1241ed5d8ad2539b1e070")
//  val obj2 = new ObjectId("56f1241ed5d8ad2539b1e069")
//  val obj3 = new ObjectId("56f1241ed5d8ad2539b1e070")
//  println(Seq(obj1, obj2, obj3).distinct)

//  val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
//  val list = Seq(1, 2, 3, 4, 5)
//  println(list)
//  println(list.sum)
//  private def product(a: Int, b: Int) = a * b
//  println(list.reduce(product))
//  println(List.empty[Int].reduce(product))
//  val (one, two) = (duration2ms("00:11:00"), duration2ms("00:22:00"))
//  println(one)
//  println(two)
//  val total = one + two
//  println(total)
//  println(ms2duration(total))
//  val d1 = ms2duration(0)
//  val d2 = ms2duration(11L * 60 * 1000)
//  val d3 = ms2duration(11L * 60 * 60 * 1000)
//  val d4 = ms2duration(11L * 24 * 60 * 60 * 1000)
//  val d5 = ms2duration(duration2ms(d1) + duration2ms(d2) + duration2ms(d3) + duration2ms(d4))
//  println(s"$d1 $d2 $d3 $d4 $d5")

//  val q = Map("phase_id" -> Map("$exists" -> false),
//    "activity_id" -> "activityOid", "action_name" -> Map("$exists" -> false))
//
//  val assertions = q.toSeq.filterNot(_._2.isInstanceOf[Map[String, _]]).toMap
//
//  println(assertions)

//  def catSubcat(): Unit = {
//    val docsWithCategories: Seq[DynDoc] = BWMongoDB3.document_master.find(Map("category" -> Map("$exists" -> true)))
//    println(docsWithCategories.length)
//    val catSubcats = docsWithCategories.map(doc => {
//      if (doc.has("subcategory"))
//        (doc.category[String], doc.subcategory[String])
//      else
//        (doc.category[String], "")
//    }).distinct
//
//    def space2dash(in: String) = in.replaceAll("\\s+", "-")
//
//    val csvLines: Seq[String] = catSubcats.
//      map(p => {
//        val cat = space2dash(p._1)
//        val subCat = space2dash(p._2)
//        s""""$cat.$subCat""""
//      })
//    println(csvLines.mkString(", "))
//  }

//catSubcat()

//  val labels = Seq("s", "s-", "-", "e---b", "and", "OR", "sanjay", "e10-b", "Sanjay", "E10-B", "-E10-B", "E10-B-")
//
//  for (label <- labels) {
//    println(s"$label: ${isValidLabel(label)}")
//  }

object ScratchPad extends App {

  import scala.util.parsing.combinator.RegexParsers

  object labelParser extends RegexParsers {

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

    def eval(expr: String, set: Set[String]): String = {
      parse(expr) match {
        case Success(test, _) => test(set).toString
        case NoSuccess(msg, _) => msg
      }
    }
  }


  val strings = Seq(
    "alpha-10", "not beta3", "alpha.two or beta3 or theta", "alpha.two and beta3 and theta",
    "not (alpha AnD beta3)", "(not beta3 not) and (not ( (alpha AND beta3)))",
    "(not beta3) and (not alpha)", "(not beta) or (not alpha)",
    "(not beta.3) or (not theta)", "andy and knot"
  )

  val labels = Set("alpha-10", "alpha", "alpha.two", "beta3", "beta.3", "beta", "gamma")

  for (string <- strings) {
    println((string, labelParser.parse(string).successful, labelParser.eval(string, labels)))
  }

}

