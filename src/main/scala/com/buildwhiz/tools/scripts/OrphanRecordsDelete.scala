package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.{PersonApi, PhaseApi, ProcessApi}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.HttpUtils
import org.bson.types.ObjectId

import java.io.PrintWriter
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

object OrphanRecordsDelete extends HttpUtils {

  private val projects: Seq[DynDoc] = BWMongoDB3.projects.find()
  private val phases: Seq[DynDoc] = BWMongoDB3.phases.find()
  private val sp2 = "&nbsp;" * 2
  private val sp4 = sp2 * 2

  private def deleteOrphanedPhases(request: HttpServletRequest, writer: PrintWriter, go: Boolean): Unit = {
    writer.println(s"${sp2}ENTRY deleteOrphanedPhases()<br/>")
    val expectedPhaseOids: Many[ObjectId] = projects.flatMap(_.phase_ids[Many[ObjectId]]).distinct
    writer.println(s"${sp4}Expected phase Oids (${expectedPhaseOids.length}): " +
        expectedPhaseOids.mkString(", ") + "<br/>")
    val existingPhases: Seq[DynDoc] = BWMongoDB3.phases.find()
    val existingPhaseOids: Seq[ObjectId] = existingPhases.map(_._id[ObjectId])
    val orphanedPhaseOids: Seq[ObjectId] = existingPhaseOids.filterNot(expectedPhaseOids.toSet.contains)
    writer.println(s"${sp4}Orphaned phase Oids (${orphanedPhaseOids.length}): " +
      orphanedPhaseOids.mkString(", ") + "<br/>")
    if (go && orphanedPhaseOids.nonEmpty) {
      writer.println(s"${sp4}Deleting ${orphanedPhaseOids.length} orphaned phases<br/>")
      val phaseByOid = existingPhases.map(p => (p._id[ObjectId], p)).toMap
      orphanedPhaseOids.foreach(oid => PhaseApi.delete(phaseByOid(oid), request))
    }
    val missingPhaseOids: Seq[ObjectId] = expectedPhaseOids.filterNot(existingPhaseOids.toSet.contains)
    writer.println(s"${sp4}Missing phase Oids (${missingPhaseOids.length}): " +
      missingPhaseOids.mkString(", ") + "<br/>")
    writer.println(s"${sp2}EXIT deleteOrphanedPhases()<br/><br/>")
  }

  private def deleteOrphanedProcesses(request: HttpServletRequest, writer: PrintWriter, go: Boolean):
      Unit = {
    writer.println(s"${sp2}ENTRY deleteOrphanedProcesses()<br/>")
    val expectedProcessOids: Many[ObjectId] = phases.flatMap(_.process_ids[Many[ObjectId]]).distinct
    writer.println(s"${sp4}Expected process Oids (${expectedProcessOids.length}): " +
        expectedProcessOids.mkString(", ") + "<br/>")
    val existingProcesses: Seq[DynDoc] = BWMongoDB3.processes.find()
    val existingProcessOids: Seq[ObjectId] = existingProcesses.map(_._id[ObjectId])
    val orphanedProcessOids: Seq[ObjectId] = existingProcessOids.filterNot(expectedProcessOids.toSet.contains)
    writer.println(s"${sp4}Orphaned process Oids (${orphanedProcessOids.length}): " +
      orphanedProcessOids.mkString(", ") + "<br/>")
    if (go && orphanedProcessOids.nonEmpty) {
      writer.println(s"${sp4}Deleting ${orphanedProcessOids.length} orphaned processes<br/>")
      val processByOid = existingProcesses.map(p => (p._id[ObjectId], p)).toMap
      orphanedProcessOids.foreach(oid => ProcessApi.delete(processByOid(oid), request))
    }
    val missingProcessOids: Seq[ObjectId] = expectedProcessOids.filterNot(existingProcessOids.toSet.contains)
    writer.println(s"${sp4}Missing process Oids (${missingProcessOids.length}): " +
      missingProcessOids.mkString(", ") + "<br/>")
    writer.println(s"${sp2}EXIT deleteOrphanedProcesses()<br/><br/>")
  }

  private def deleteOrphanedActivities(writer: PrintWriter, go: Boolean): Unit = {
    writer.println(s"${sp2}ENTRY deleteOrphanedActivities()<br/>")
    val processes: Seq[DynDoc] = BWMongoDB3.processes.find()
    val expectedActivityOids: Many[ObjectId] = processes.flatMap(_.activity_ids[Many[ObjectId]]).distinct
    writer.println(s"${sp4}Expected activity Oids (${expectedActivityOids.length}): " +
        expectedActivityOids.mkString(", ") + "<br/>")
    val existingActivities: Seq[DynDoc] = BWMongoDB3.activities.find()
    val existingActivityOids: Seq[ObjectId] = existingActivities.map(_._id[ObjectId])
    val orphanedActivityOids: Seq[ObjectId] = existingActivityOids.filterNot(expectedActivityOids.toSet.contains)
    writer.println(s"${sp4}Orphaned activity Oids (${orphanedActivityOids.length}): " +
        orphanedActivityOids.mkString(", ") + "<br/>")
    if (go && orphanedActivityOids.nonEmpty) {
      writer.println(s"${sp4}Deleting ${orphanedActivityOids.length} orphaned activities<br/>")
      val deleteResult = BWMongoDB3.activities.deleteMany(Map("_id" -> Map("$in" -> orphanedActivityOids)))
      if (deleteResult.getDeletedCount != orphanedActivityOids.length) {
        writer.println(s"${sp4}Deleted only ${deleteResult.getDeletedCount} orphaned activities<br/>")
      }
    }
    val missingActivityOids: Seq[ObjectId] = expectedActivityOids.filterNot(existingActivityOids.toSet.contains)
    writer.println(s"${sp4}Missing activity Oids (${missingActivityOids.length}): " +
      missingActivityOids.mkString(", ") + "<br/>")
    writer.println(s"${sp2}EXIT deleteOrphanedActivities()<br/><br/>")
  }

  private def deleteOrphanedDeliverables(writer: PrintWriter, go: Boolean): Unit = {
    writer.println(s"${sp2}ENTRY deleteOrphanedDeliverables()<br/>")
    val existingDeliverables: Seq[DynDoc] = BWMongoDB3.deliverables.find()
    val parentActivityOids: Seq[ObjectId] = existingDeliverables.map(_.activity_id[ObjectId]).distinct
    val existingParentActivities: Seq[DynDoc] = BWMongoDB3.activities.find(Map("_id" -> Map($in -> parentActivityOids)))
    val existingParentActivityOids: Set[ObjectId] = existingParentActivities.map(_._id[ObjectId]).toSet
    val orphanedDeliverables = existingDeliverables.
        filterNot(d => existingParentActivityOids.contains(d.activity_id[ObjectId]))
    val orphanedDeliverableOids: Seq[ObjectId] = orphanedDeliverables.map(_._id[ObjectId])
    writer.println(s"${sp4}Orphaned deliverable Oids (${orphanedDeliverableOids.length}): " +
      orphanedDeliverableOids.mkString(", ") + "<br/>")
    if (go && orphanedDeliverables.nonEmpty) {
      writer.println(s"${sp4}Deleting ${orphanedDeliverables.length} orphaned deliverables<br/>")
      val deleteResult = BWMongoDB3.deliverables.deleteMany(Map("_id" -> Map("$in" -> orphanedDeliverableOids)))
      if (deleteResult.getDeletedCount != orphanedDeliverableOids.length) {
        writer.println(s"${sp4}Deleted only ${deleteResult.getDeletedCount} orphaned deliverables<br/>")
      }
    }
    writer.println(s"${sp2}EXIT deleteOrphanedDeliverables()<br/><br/>")
  }

  private def deleteOrphanedDocs(writer: PrintWriter, go: Boolean): Unit = {
    writer.println(s"${sp2}ENTRY deleteOrphanedDocs()<br/>")
    val projectOids = projects.map(_._id[ObjectId])
    val projectOrphanedDocs: Seq[DynDoc] = BWMongoDB3.document_master.
        find(Map($and -> Seq(Map("project_id" -> Map($exists -> true)),
          Map("project_id" -> Map($not -> Map($in -> projectOids))))))
    val projectOrphanedDocOids = projectOrphanedDocs.map(_._id[ObjectId])
    writer.println(s"${sp4}Project-Orphaned doc Oids (${projectOrphanedDocOids.length}): " +
      projectOrphanedDocOids.mkString(", ") + "<br/>")
    val phaseOids = phases.map(_._id[ObjectId])
    val phaseOrphanedDocs: Seq[DynDoc] = BWMongoDB3.document_master.
        find(Map($and -> Seq(Map("phase_id" -> Map($exists -> true)),
          Map("phase_id" -> Map($not -> Map($in -> phaseOids))))))
    val phaseOrphanedDocOids = phaseOrphanedDocs.map(_._id[ObjectId])
    writer.println(s"${sp4}Phase-Orphaned doc Oids (${phaseOrphanedDocOids.length}): " +
      phaseOrphanedDocOids.mkString(", ") + "<br/>")
    val orphanedDocOids: Seq[ObjectId] = projectOrphanedDocOids ++ phaseOrphanedDocOids
    writer.println(s"${sp4}ALL Orphaned doc Oids (${orphanedDocOids.length}): " +
      orphanedDocOids.mkString(", ") + "<br/>")
    if (go && orphanedDocOids.nonEmpty) {
      writer.println(s"${sp4}Deleting ${orphanedDocOids.length} orphaned docs<br/>")
      val deleteResult = BWMongoDB3.document_master.deleteMany(Map("_id" -> Map("$in" -> orphanedDocOids)))
      if (deleteResult.getDeletedCount != orphanedDocOids.length) {
        writer.println(s"${sp4}Deleted only ${deleteResult.getDeletedCount} orphaned docs<br/>")
      }
    }
    writer.println(s"${sp2}EXIT deleteOrphanedDocs()<br/><br/>")
  }

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    response.setContentType("text/html")
    val writer = response.getWriter
    writer.println("<html><body><tt>")
    writer.println(s"ENTRY ${getClass.getName}:main()<br/><br/>")
    val user: DynDoc = getUser(request)
    if (!PersonApi.isBuildWhizAdmin(Right(user)) || user.first_name[String] != "Sanjay") {
      throw new IllegalArgumentException("Not permitted")
    }
    val go: Boolean = args.length == 1 && args(0) == "GO"
    deleteOrphanedPhases(request, writer, go)
    deleteOrphanedProcesses(request, writer, go)
    deleteOrphanedActivities(writer, go)
    deleteOrphanedDeliverables(writer, go)
    deleteOrphanedDocs(writer, go)
    writer.println(s"EXIT ${getClass.getName}:main()<br/>")
    writer.println("</tt></body></html>")
    writer.flush()
  }

}
