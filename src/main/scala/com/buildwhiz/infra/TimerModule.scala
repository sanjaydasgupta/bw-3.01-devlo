package com.buildwhiz.infra

import java.lang.management.ManagementFactory
import java.util.{Timer, TimerTask}

import com.buildwhiz.utils.{BWLogger, HttpUtils}

object TimerModule extends HttpUtils {

  val timerTickInMilliseconds: Long = 1 * 60 * 1000L // 1 minute

  private def newDay(ms: Long): Unit = {
    BWLogger.log(classOf[TimerTask].getSimpleName, "run", s"Midnight-Tick")
  }

  private def fifteenMinutes(ms: Long): Unit = {
    BWLogger.log(classOf[TimerTask].getSimpleName, "fifteenMinutes", "15-Minute-Tick", performanceData(): _*)
    val millisecondsInDay = 24 * 60 * 60 * 1000L
    val msInDay = ms % millisecondsInDay
    if (msInDay > millisecondsInDay - 5000 || msInDay < 20000)
      newDay(ms)
  }

  private def performanceData(): Seq[(String, String)] = {
    val runtime = sys.runtime
    val (freeMemory, maxMemory) = (runtime.freeMemory(), runtime.maxMemory())
    val sysLoadAvg = ManagementFactory.getOperatingSystemMXBean.getSystemLoadAverage
    val threadCount = Thread.activeCount()
    Seq("Free-Memory" -> freeMemory, "Max-Memory" -> maxMemory, "Thread-Count" -> threadCount,
      "Sys-Load-Avg" -> sysLoadAvg).map(kv => (kv._1, kv._2.toString))
  }

  private def timerTicks(): Unit = {
    //BWLogger.log(classOf[TimerTask].getSimpleName, "run", s"Timer-Tick ($logMessage)")
    val ms: Long = System.currentTimeMillis()
    val millisecondsIn15Minutes = 15 * 60 * 1000
    val msModulo15Minutes = (ms % millisecondsIn15Minutes).asInstanceOf[Int]
    if (msModulo15Minutes > millisecondsIn15Minutes - 5000 || msModulo15Minutes < 20000)
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
