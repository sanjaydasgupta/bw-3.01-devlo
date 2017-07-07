package com.buildwhiz

import javax.mail.internet.InternetAddress

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.DateTimeUtils

import scala.collection.JavaConverters._
import org.bson.types.ObjectId
import org.bson.Document
import org.camunda.bpm.engine.ProcessEngines

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

object ScratchPad extends App with DateTimeUtils {
  val rts = ProcessEngines.getDefaultProcessEngine.getRuntimeService
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
}
