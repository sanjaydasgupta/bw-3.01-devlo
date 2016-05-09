package com.buildwhiz

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

trait DateTimeUtils {

  def dateTimeString(milliSeconds: Long, timeZoneCode: Option[String] = Some("GMT")): String = {
    val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm z")
    timeZoneCode.foreach(tzc => simpleDateFormat.setTimeZone(TimeZone.getTimeZone(tzc)))
    simpleDateFormat.format(new Date(milliSeconds))
  }

}
