package com.buildwhiz.jelly

import com.buildwhiz.infra.BWLogger
import org.camunda.bpm.engine.delegate.{DelegateExecution, JavaDelegate}

class ArchiveProject extends JavaDelegate {

  def execute(de: DelegateExecution): Unit = {
    BWLogger.log(getClass.getName, "execute()", "ENTRY", de)
    BWLogger.log(getClass.getName, "execute()", "EXIT-OK", de)
  }

}
