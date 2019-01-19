package com.buildwhiz.utils

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

trait DateTimeUtils {

  def dateTimeString(milliSeconds: Long, timeZoneCode: Option[String] = Some("GMT")): String = {
    val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
    timeZoneCode.foreach(tzc => simpleDateFormat.setTimeZone(TimeZone.getTimeZone(tzc)))
    simpleDateFormat.format(new Date(milliSeconds))
  }

  private val durationRe = "(\\d+):(\\d+):(\\d+)".r

  def duration2ms(duration: String): Long = duration match {
    case durationRe(days, hours, minutes) => ((days.toInt * 24L + hours.toInt) * 60L + minutes.toInt) * 60000L
  }

  def ms2duration(ms: Long): String = {
    val days = ms / 86400000L
    val residue = ms - days * 86400000L
    val hours = residue / 3600000L
    val minutes = (residue - hours * 3600000L) / 60000L
    f"$days%02d:$hours%02d:$minutes%02d"
  }

  def duration2iso(duration: String): String = {
    val Array(days, hours, minutes) = duration.split(":").map(_.toInt)
    s"P${days}DT${hours}H${minutes}M"
  }

}
