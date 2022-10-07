package com.buildwhiz.utils

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._

import org.bson.Document

trait ProjectUtils extends DateTimeUtils {

  def getActivityDuration(activity: DynDoc): String = {
    val actions = Seq.empty[DynDoc]//: Seq[DynDoc] = activity.actions[Many[Document]]
    val prerequisitesMs = actions.filter(_.`type`[String] == "prerequisite") match {
      case Nil => 0
      case prerequisites => prerequisites.map(p => duration2ms(p.duration[String])).max
    }
    val reviewsMs = actions.filter(_.`type`[String] == "review") match {
      case Nil => 0
      case reviews => reviews.map(p => duration2ms(p.duration[String])).max
    }
    val mainMs = actions.filter(_.`type`[String] == "main") match {
      case Nil => 0
      case reviews => reviews.map(p => duration2ms(p.duration[String])).max
    }
    ms2duration(prerequisitesMs + reviewsMs + mainMs)
  }

}
