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
    val expectedPhaseOids: Many[ObjectId] = projects.flatMap(_.phase_ids[Many[ObjectId]]).distinct
    response.getWriter.println(s"\tExpected phase Oids (${expectedPhaseOids.length}): " +
        expectedPhaseOids.mkString(", "))
    val existingPhases: Seq[DynDoc] = BWMongoDB3.phases.find()
    val existingPhaseOids: Seq[ObjectId] = existingPhases.map(_._id[ObjectId])
    val orphanedPhaseOids: Seq[ObjectId] = existingPhaseOids.filterNot(expectedPhaseOids.toSet.contains)
    response.getWriter.println(s"\tOrphaned phase Oids (${orphanedPhaseOids.length}): " +
      orphanedPhaseOids.mkString(", "))
    val missingPhaseOids: Seq[ObjectId] = expectedPhaseOids.filterNot(existingPhaseOids.toSet.contains)
    response.getWriter.println(s"\tMissing phase Oids (${missingPhaseOids.length}): " +
      missingPhaseOids.mkString(", "))
    response.getWriter.println(s"EXIT deleteOrphanedPhases()\n\n")
  }

  private def deleteOrphanedProcesses(request: HttpServletRequest, response: HttpServletResponse, go: Boolean):
      Unit = {
    response.getWriter.println(s"ENTRY deleteOrphanedProcesses()")
    val phases: Seq[DynDoc] = BWMongoDB3.phases.find()
    val expectedProcessOids: Many[ObjectId] = phases.flatMap(_.process_ids[Many[ObjectId]]).distinct
    response.getWriter.println(s"\tExpected process Oids (${expectedProcessOids.length}): " +
        expectedProcessOids.mkString(", "))
    val existingProcesses: Seq[DynDoc] = BWMongoDB3.processes.find()
    val existingProcessOids: Seq[ObjectId] = existingProcesses.map(_._id[ObjectId])
    val orphanedProcessOids: Seq[ObjectId] = existingProcessOids.filterNot(expectedProcessOids.toSet.contains)
    response.getWriter.println(s"\tOrphaned process Oids (${orphanedProcessOids.length}): " +
      orphanedProcessOids.mkString(", "))
    val missingProcessOids: Seq[ObjectId] = expectedProcessOids.filterNot(existingProcessOids.toSet.contains)
    response.getWriter.println(s"\tMissing process Oids (${missingProcessOids.length}): " +
      missingProcessOids.mkString(", "))
    response.getWriter.println(s"EXIT deleteOrphanedProcesses()\n\n")
  }

  private def deleteOrphanedActivities(request: HttpServletRequest, response: HttpServletResponse, go: Boolean):
      Unit = {
    response.getWriter.println("ENTRY deleteOrphanedActivities()")
    val processes: Seq[DynDoc] = BWMongoDB3.processes.find()
    val expectedActivityOids: Many[ObjectId] = processes.flatMap(_.activity_ids[Many[ObjectId]]).distinct
    response.getWriter.println(s"\tExpected activity Oids (${expectedActivityOids.length}): " +
        expectedActivityOids.mkString(", "))
    val existingActivities: Seq[DynDoc] = BWMongoDB3.activities.find()
    val existingActivityOids: Seq[ObjectId] = existingActivities.map(_._id[ObjectId])
    val orphanedActivityOids: Seq[ObjectId] = existingActivityOids.filterNot(expectedActivityOids.toSet.contains)
    response.getWriter.println(s"\tOrphaned activity Oids (${orphanedActivityOids.length}): " +
        orphanedActivityOids.mkString(", "))
    val missingActivityOids: Seq[ObjectId] = expectedActivityOids.filterNot(existingActivityOids.toSet.contains)
    response.getWriter.println(s"\tMissing activity Oids (${missingActivityOids.length}): " +
      missingActivityOids.mkString(", "))
    response.getWriter.println("EXIT deleteOrphanedActivities()\n\n")
  }

  private def deleteOrphanedDeliverables(request: HttpServletRequest, response: HttpServletResponse, go: Boolean):
      Unit = {
    response.getWriter.println(s"ENTRY deleteOrphanedDeliverables()")
    response.getWriter.println(s"EXIT deleteOrphanedDeliverables()\t\t")
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
