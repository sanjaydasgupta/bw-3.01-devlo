package com.buildwhiz.infra

import java.lang.management.ManagementFactory
import java.util.{Timer, TimerTask}

import com.buildwhiz.utils.{BWLogger, HttpUtils}

object TimerModule extends HttpUtils {

  val timerTickInMilliseconds: Long = 1 * 60 * 1000L // 1 minute

  private def logMessage: String = {
    val runtime = sys.runtime
    val freeMemory = runtime.freeMemory()
    val maxMemory = runtime.maxMemory()
    val threadCount = Thread.activeCount()
    val systemLoadAverage = ManagementFactory.getOperatingSystemMXBean.getSystemLoadAverage
    s"Threads: $threadCount; Max-Mem: ${by3(maxMemory)}; Free-Mem: ${by3(freeMemory)}; Avg-Sys-Load: $systemLoadAverage"
  }

  private def newDay(ms: Long): Unit = {
    BWLogger.log(classOf[TimerTask].getSimpleName, "run", s"Midnight-Tick")
  }

  private def fifteenMinutes(ms: Long): Unit = {
    BWLogger.log(classOf[TimerTask].getSimpleName, "run", s"15-Minute-Tick ($logMessage)")
    //BWLogger.log(classOf[TimerTask].getSimpleName, "run", s"15-Minute-Tick")
    val millisecondsInDay = 24 * 60 * 60 * 1000L
    val msInDay = ms % millisecondsInDay
    if (msInDay > millisecondsInDay - 5000 || msInDay < 20000)
      newDay(ms)
  }

  private def timerTicks(): Unit = {
    //BWLogger.log(classOf[TimerTask].getSimpleName, "run", s"Timer-Tick ($logMessage)")
    val ms: Long = System.currentTimeMillis()
    val millisecondsIn15Minutes = 15 * 60 * 1000L
    val msIn15Minutes = ms % millisecondsIn15Minutes
    if (msIn15Minutes > millisecondsIn15Minutes - 5000 || msIn15Minutes < 20000)
      fifteenMinutes(ms)
    // perform scheduled tasks
  }

  private val bwTimer = new Timer(true)

  private object timerTask extends TimerTask {
    override def run(): Unit = {
      timerTicks()
    }
  }

  def scheduleTimer(): Unit = {
    timerTicks()
    val millisNow = System.currentTimeMillis
    val millisTillNextTimerTick = timerTickInMilliseconds - (millisNow % timerTickInMilliseconds)
    bwTimer.scheduleAtFixedRate(timerTask, millisTillNextTimerTick, timerTickInMilliseconds)
  }

  def cancelTimer(): Unit = bwTimer.cancel()
}
