package com.buildwhiz.infra

import javax.servlet.{ServletContextEvent, ServletContextListener => JavaxSCL}

class ServletContextListener extends JavaxSCL {

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    TimerModule.scheduleTimer()
  }

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
    TimerModule.cancelTimer()
  }

}
