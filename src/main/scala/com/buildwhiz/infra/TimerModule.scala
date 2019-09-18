package com.buildwhiz.infra

import java.util.{Timer, TimerTask}

import com.buildwhiz.utils.BWLogger

object TimerModule {

  private def periodicChecks(): Unit = {
    // perform timer tasks
  }

  private val bwTimer = new Timer(true)

  private object timerTask extends TimerTask {
    override def run(): Unit = {
      BWLogger.log(classOf[TimerTask].getSimpleName, "run", "Timer-Tick")
      periodicChecks()
    }
  }

  def scheduleTimer(): Unit = bwTimer.scheduleAtFixedRate(timerTask, 0, 60000L)

  def cancelTimer(): Unit = bwTimer.cancel()
}
