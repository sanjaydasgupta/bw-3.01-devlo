package com.buildwhiz.utils

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone, Calendar}

trait DateTimeUtils {

  def dateString(milliSeconds: Long, timeZoneCode: String = "GMT"): String = {
    val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
    simpleDateFormat.setTimeZone(TimeZone.getTimeZone(timeZoneCode))
    simpleDateFormat.format(new Date(milliSeconds))
  }

  def dateTimeString(milliSeconds: Long, timeZoneCode: Option[String] = Some("GMT"),
      withMilliseconds: Boolean = false): String = {
    val simpleDateFormat = if (withMilliseconds) {
      new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z")
    } else {
      new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
    }
    timeZoneCode.foreach(tzc => simpleDateFormat.setTimeZone(TimeZone.getTimeZone(tzc)))
    simpleDateFormat.format(new Date(milliSeconds))
  }

  def dateTimeStringAmerican(milliSeconds: Long, timeZoneCode: Option[String] = Some("GMT"),
      withMilliseconds: Boolean = false): String = {
    val usDateFormat = if (withMilliseconds) {
      new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS z")
    } else {
      new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z")
    }
    timeZoneCode.foreach(tzc => usDateFormat.setTimeZone(TimeZone.getTimeZone(tzc)))
    usDateFormat.format(new Date(milliSeconds))
  }

  def milliseconds(yyyymmdd: String, timeZoneCode: Option[String] = Some("GMT")): Long = {
    val Array(year, month, date) = yyyymmdd.split("-").map(_.toInt)
    val calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZoneCode.get))
    calendar.clear()
    calendar.set(year, month - 1, date, 0, 0, 0)
    calendar.getTimeInMillis
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

  def addWeekdays(baseDateMs: Long, days: Long, tz: String): Long = {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone(tz))
    calendar.setTimeInMillis(baseDateMs)
    val delta = if (days > 0) {
      1
    } else if (days < 0) {
      -1
    } else {
      0
    }
    var daysLeft = days
    while (daysLeft != 0) {
      calendar.add(Calendar.DATE, delta)
      val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
      if (dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY) {
        daysLeft -= delta
      }
    }
    calendar.getTimeInMillis
  }

}

object DateTimeUtilsTest extends App with DateTimeUtils {
  val ms = System.currentTimeMillis()
  println(s"GMT: ${dateTimeString(ms)}")
  println(s"""Kolkata: ${dateTimeString(ms, Some("Asia/Kolkata"))}""")
  println(s"""Pacific: ${dateTimeString(ms, Some("US/Pacific"))}""")
  println("******************************************")
  println(s"GMT: ${milliseconds("2020-01-13")}")
  println(s"""Kolkata: ${milliseconds("2020-01-13", Some("Asia/Kolkata"))}""")
  println(s"""Pacific: ${milliseconds("2020-01-13", Some("US/Pacific"))}""")
  println("******************************************")
  val utcMillis = milliseconds("2020-01-13")
  println(s"GMT: ${dateTimeString(utcMillis)}")
  println(s"""Kolkata: ${dateTimeString(utcMillis, Some("Asia/Kolkata"))}""")
  println(s"""Pacific: ${dateTimeString(utcMillis, Some("US/Pacific"))}""")
}
