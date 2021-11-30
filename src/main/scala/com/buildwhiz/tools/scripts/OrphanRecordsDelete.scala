package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.HttpUtils
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

object OrphanRecordsDelete extends HttpUtils {

  private def deleteOrphanedPhases(request: HttpServletRequest, response: HttpServletResponse, go: Boolean): Unit = {
    response.getWriter.println(s"ENTRY deleteOrphanedPhases()")
    val projects: Seq[DynDoc] = BWMongoDB3.projects.find()
    val phaseOids: Many[ObjectId] = projects.flatMap(_.phase_ids[Many[ObjectId]]).distinct
    response.getWriter.println(s"""\tKnown phase Oids (${phaseOids.length}): ${phaseOids.mkString(", ")}""")
    val orphanedPhases: Seq[DynDoc] =
      BWMongoDB3.phases.find(Map("_id" -> Map($not -> Map($in -> phaseOids))))
    val orphanedPhaseOids: Seq[ObjectId] = orphanedPhases.map(_._id[ObjectId])
    response.getWriter.println(s"\tOrphaned phase Oids (${orphanedPhaseOids.length}): " +
      orphanedPhaseOids.mkString(", "))
    response.getWriter.println(s"EXIT deleteOrphanedPhases()\n\n")
  }

  private def deleteOrphanedProcesses(request: HttpServletRequest, response: HttpServletResponse, go: Boolean): Unit = {
    response.getWriter.println(s"ENTRY deleteOrphanedProcesses()")
    val phases: Seq[DynDoc] = BWMongoDB3.phases.find()
    val processOids: Many[ObjectId] = phases.flatMap(_.process_ids[Many[ObjectId]]).distinct
    response.getWriter.println(s"""\tValid process Oids (${processOids.length}): ${processOids.mkString(", ")}""")
    val orphanedProcesses: Seq[DynDoc] =
      BWMongoDB3.processes.find(Map("_id" -> Map($not -> Map($in -> processOids))))
    val orphanedProcessOids: Seq[ObjectId] = orphanedProcesses.map(_._id[ObjectId])
    response.getWriter.println(s"\tOrphaned process Oids (${orphanedProcessOids.length}): " +
      orphanedProcessOids.mkString(", "))
    response.getWriter.println(s"EXIT deleteOrphanedProcesses()\n\n")
  }

  private def deleteOrphanedActivities(request: HttpServletRequest, response: HttpServletResponse, go: Boolean):
      Unit = {
    response.getWriter.println("ENTRY deleteOrphanedActivities()")
    val processes: Seq[DynDoc] = BWMongoDB3.processes.find()
    val activityOids: Many[ObjectId] = processes.flatMap(_.activity_ids[Many[ObjectId]]).distinct
    response.getWriter.println(s"""\tValid activity Oids (${activityOids.length}): ${activityOids.mkString(", ")}""")
    val orphanedActivities: Seq[DynDoc] =
        BWMongoDB3.activities.find(Map("_id" -> Map($not -> Map($in -> activityOids))))
    val orphanedActivityOids: Seq[ObjectId] = orphanedActivities.map(_._id[ObjectId])
    response.getWriter.println(s"\tOrphaned activity Oids (${orphanedActivityOids.length}): " +
        orphanedActivityOids.mkString(", "))
    response.getWriter.println("EXIT deleteOrphanedActivities()\n\n")
  }

  private def deleteOrphanedDeliverables(request: HttpServletRequest, response: HttpServletResponse, go: Boolean): Unit = {
    response.getWriter.println(s"ENTRY deleteOrphanedDeliverables()")
    response.getWriter.println(s"EXIT deleteOrphanedDeliverables()")
  }

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    response.getWriter.println(s"ENTRY ${getClass.getName}:main()\n\n")
    val user: DynDoc = getUser(request)
    if (!PersonApi.isBuildWhizAdmin(Right(user)) || user.first_name[String] != "Sanjay") {
      throw new IllegalArgumentException("Not permitted")
    }
    val go: Boolean = args.length == 1 && args(0) == "GO"
    deleteOrphanedPhases(request, response, go)
    deleteOrphanedProcesses(request, response, go)
    deleteOrphanedActivities(request, response, go)
    deleteOrphanedDeliverables(request, response, go)
    response.getWriter.println(s"EXIT ${getClass.getName}:main()")
  }

}
