package com.buildwhiz.tools.scripts

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

object ActivityAssignmentsCleanUp {

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    val knownProjects: Seq[DynDoc] = BWMongoDB3.projects.find()
    val knownProjectOids = knownProjects.map(_._id[ObjectId]).asJava

    val knownPhases: Seq[DynDoc] = BWMongoDB3.phases.find()
    val knownPhaseOids = knownPhases.map(_._id[ObjectId]).asJava

    val knownProcesses: Seq[DynDoc] = BWMongoDB3.processes.find()
    val knownProcessOids = knownProcesses.map(_._id[ObjectId]).asJava

    val query = Map($or -> Seq(Map("project_id" -> Map($not -> Map($in -> knownProjectOids))),
      Map("phase_id" -> Map($not -> Map($in -> knownPhaseOids))),
      Map("process_id" -> Map($not -> Map($in -> knownProcessOids)))))

    val zombieAssignments: Seq[DynDoc] = BWMongoDB3.activity_assignments.find(query)

    val zombieProcessOids = zombieAssignments.map(_.process_id[ObjectId]).distinct
    if (args.length == 1 && args(0) == "delete") {
      response.getWriter.println(s"""found ${zombieAssignments.length} zombie activity-assignments""")
      response.getWriter.println(s"""zombie process_ids: ${zombieProcessOids.mkString(", ")}""")
      val deleteResult = BWMongoDB3.activity_assignments.deleteMany(query)
      response.getWriter.println(s"""deleted ${deleteResult.getDeletedCount} zombie activity-assignments""")
    } else {
      response.getWriter.println(s"""found ${zombieAssignments.length} zombie activity-assignments""")
      response.getWriter.println(s"""zombie process_ids: ${zombieProcessOids.mkString(", ")}""")
    }
  }

}
