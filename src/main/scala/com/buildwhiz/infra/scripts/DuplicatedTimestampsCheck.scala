package com.buildwhiz.infra.scripts

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._

import scala.collection.JavaConverters._

object DuplicatedTimestampsCheck extends App {

  val docRecs: Seq[DynDoc] = BWMongoDB3.document_master.find(Map("versions" -> Map("$exists" -> true))).asScala.toSeq
  println(s"with versions: ${docRecs.length}")
  val withBadTimestamps = docRecs.filter(d => {
    val versions: Seq[DynDoc] = d.versions[DocumentList]
    val timestamps: Set[Long] = versions.map(_.timestamp[Long]).toSet
    println(s"versions: ${versions.length}, ts: ${timestamps.size}")
    timestamps.size != versions.length
  })
  println(withBadTimestamps.length)

}