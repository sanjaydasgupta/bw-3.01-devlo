package com.buildwhiz.infra

import com.buildwhiz.slack.SlackApi

import javax.servlet.{ServletContextEvent, ServletContextListener => JavaxSCL}

class ServletContextListener extends JavaxSCL {

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    TimerModule.scheduleTimer()
    SlackApi.pushHomePages()
  }

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
    TimerModule.cancelTimer()
  }

}
