package com.buildwhiz.infra

import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.util.{Timer, TimerTask}

import com.buildwhiz.utils.{BWLogger, HttpUtils}

object TimerModule extends HttpUtils {

  val timerPeriodInMilliseconds: Long = 15 * 60 * 1000L // 15 minutes

  private def logMessage: String = {
    val startTime = ManagementFactory.getRuntimeMXBean.getStartTime
    val systemLoadAverage = ManagementFactory.getOperatingSystemMXBean.getSystemLoadAverage
    val hostname = InetAddress.getLocalHost.getHostName
    val runtime = sys.runtime
    val processors = runtime.availableProcessors()
    val freeMemory = runtime.freeMemory()
    val maxMemory = runtime.maxMemory()
    val threadCount = Thread.activeCount()
    s"Threads: $threadCount; Max-Mem: ${by3(maxMemory)}; Free-Mem: ${by3(freeMemory)}; Host: $hostname; Processors: $processors"
  }

  private def periodicChecks(): Unit = {
    BWLogger.log(classOf[TimerTask].getSimpleName, "run", s"Timer-Tick ($logMessage)")
    // perform scheduled tasks
  }

  private val bwTimer = new Timer(true)

  private object timerTask extends TimerTask {
    override def run(): Unit = {
      periodicChecks()
    }
  }

  def scheduleTimer(): Unit = {
    periodicChecks()
    val millisNow = System.currentTimeMillis
    val millisTillNextTimerTick = timerPeriodInMilliseconds - (millisNow % timerPeriodInMilliseconds)
    bwTimer.scheduleAtFixedRate(timerTask, millisTillNextTimerTick, timerPeriodInMilliseconds)
  }

  def cancelTimer(): Unit = bwTimer.cancel()
}
