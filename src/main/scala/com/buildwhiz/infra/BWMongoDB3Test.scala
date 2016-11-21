package com.buildwhiz.infra

import BWMongoDB3._
import org.bson.Document

import scala.collection.mutable

object BWMongoDB3Test extends App {
  //type MapType = Map[String, AnyRef]
  type MapType = mutable.Map[String, AnyRef]
  val d = new Document("key", "keyValue").append("name", "sanjay").append("age", 99).
    append("addr", new Document("l1", "flat28c").append("line2", "tower-1").append("line3", "southcity"))
  //val m: mutable.Map[String, AnyRef] = d
  import scala.collection.JavaConverters._
  val m: MapType = d.asScala
  println(s"whole map: $m")
  println(s"addr: ${m("addr")}")
  val addr: MapType = m("addr").asInstanceOf[MapType]
  println(s"addr.line2: ${addr("line2")}")
}
