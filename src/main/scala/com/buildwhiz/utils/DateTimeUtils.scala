package com.buildwhiz.utils

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

trait DateTimeUtils {

  def dateTimeString(milliSeconds: Long, timeZoneCode: Option[String] = Some("GMT")): String = {
    val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
    timeZoneCode.foreach(tzc => simpleDateFormat.setTimeZone(TimeZone.getTimeZone(tzc)))
    simpleDateFormat.format(new Date(milliSeconds))
  }

  private val durationRe = "(\\d{2}):(\\d{2}):(\\d{2})".r

  def duration2ms(duration: String): Long = duration match {
    case durationRe(days, hours, minutes) => ((days.toInt * 24L + hours.toInt) * 60L + minutes.toInt) * 60000L
  }

  def ms2duration(ms: Long): String = {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val minutesDisplay = minutes % 60
    val hours = minutes / 60
    val hoursDisplay = hours % 24
    val days = hours / 24
    f"$days%02d:$hoursDisplay%02d:$minutesDisplay%02d"
  }

}
