package com.buildwhiz.tools.scripts

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import BWMongoDB3._

object CopyPhases2Processes extends App {
  val phases: Seq[DynDoc] = BWMongoDB3.phases.find()
  for (phase <- phases) {
    BWMongoDB3.processes.insertOne(phase.asDoc)
  }
}
