package com.buildwhiz.tools.scripts

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import BWMongoDB3._
import DynDoc._
import com.buildwhiz.utils.BWLogger
import org.bson.types.ObjectId

object RefactorPhases2Processes extends App {
  BWLogger.log(getClass.getName, "main", "ENTRY")
  val deleteResult = BWMongoDB3.processes.deleteMany(Map.empty[String, AnyRef])
  BWLogger.log(getClass.getName, "main",
      s"Deleted ${deleteResult.getDeletedCount} process objects")
  val phases: Seq[DynDoc] = BWMongoDB3.phases.find().toList
  for (phase <- phases) {
    BWMongoDB3.processes.insertOne(phase.asDoc)
  }
  BWLogger.log(getClass.getName, "main",
    s"Copied ${phases.length} process objects")
  val projects: Seq[DynDoc] = BWMongoDB3.projects.find()
  for (project <- projects) {
    if (project.has("phase_ids")) {
      val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> project._id[ObjectId]),
          Map("$rename" -> Map("phase_ids" -> "process_ids")))
      BWLogger.log(getClass.getName, "main",
        s"Renamed ${updateResult.getModifiedCount} field(s) in '${project.name[String]}'")
    } else {
      BWLogger.log(getClass.getName, "main",
        s"Field 'phase_ids' not found in '${project.name[String]}'")
    }
  }
  BWLogger.log(getClass.getName, "main", "EXIT")
}
