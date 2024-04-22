package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.{PersonApi, PhaseApi, ProcessApi}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.HttpUtils
import org.bson.Document
import org.bson.types.ObjectId

import java.io.PrintWriter
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import scala.annotation.unused
import scala.jdk.CollectionConverters._

@unused
object OrphanRecordsDelete extends HttpUtils {

  private val sp2 = "&nbsp;" * 2
  private val sp4 = sp2 * 2

  private def deleteOrphanedFolderAccessGrants(writer: PrintWriter, go: Boolean, detail: Boolean): Unit = {
    writer.println(s"${sp2}ENTRY deleteOrphanedFolderAccessGrants()<br/>")
    val aggrPipe: Seq[Document] = Seq(
      Map("$group" -> Map("_id" -> "$phase_id", "use_count" -> Map("$sum" -> 1))),
      Map("$lookup" -> Map("from" -> "phases", "localField" -> "_id", "foreignField" -> "_id", "as" -> "phase")),
      Map("$project" -> Map("phase_id" -> "$_id", "use_count" -> true, "phase_exists" -> Map("$size" -> "$phase")))
    )
    val aggr: Seq[DynDoc] = BWMongoDB3.folder_access_grants.aggregate(aggrPipe)
    val orphanPhaseRecords = aggr.filter(_.phase_exists[Int] == 0)
    val orphanRecordCount = orphanPhaseRecords.map(_.use_count[Int]).sum
    writer.println(s"${sp4}Found $orphanRecordCount orphaned records associated with ${orphanPhaseRecords.length} phases ...<br/>")
    for (opr <- orphanPhaseRecords) {
      writer.println(s"""$sp4 - Non-existing phase with _id=${opr._id[ObjectId]} => ${opr.use_count[Int]} records <br/>""")
    }
    if (go && orphanPhaseRecords.nonEmpty) {
      writer.println(s"${sp4}Deleting $orphanRecordCount orphaned records ...<br/>")
      val orphanedPhaseOids: Many[ObjectId] = orphanPhaseRecords.map(_.phase_id[ObjectId]).asJava
      val deleteResult = BWMongoDB3.folder_access_grants.deleteMany(Map("phase_id" -> Map($in -> orphanedPhaseOids)))
      if (deleteResult.getDeletedCount != orphanRecordCount) {
        writer.println(s"${sp4}Deleted only ${deleteResult.getDeletedCount} orphaned records<br/>")
      } else {
        writer.println(s"${sp4}Deleted ALL $orphanRecordCount orphaned records<br/>")
      }
    }
    writer.println(s"${sp2}EXIT deleteOrphanedFolderAccessGrants()<br/><br/>")
  }

  private def deleteOrphanedPhases(request: HttpServletRequest, writer: PrintWriter, go: Boolean,
      detail: Boolean): Unit = {
    writer.println(s"${sp2}ENTRY deleteOrphanedPhases()<br/>")
    val projects: Seq[DynDoc] = BWMongoDB3.projects.find()
    val expectedPhaseOids: Many[ObjectId] = projects.flatMap(_.phase_ids[Many[ObjectId]]).distinct
    writer.println(s"${sp4}Expected phase Oids (${expectedPhaseOids.length})" +
      (if (detail) expectedPhaseOids.mkString(": ", ", ", "") else "") + "<br/>")
    val existingPhases: Seq[DynDoc] = BWMongoDB3.phases.find()
    val existingPhaseOids: Seq[ObjectId] = existingPhases.map(_._id[ObjectId])
    val orphanedPhaseOids: Seq[ObjectId] = existingPhaseOids.filterNot(expectedPhaseOids.toSet.contains)
    writer.println(s"${sp4}Orphaned phase Oids (${orphanedPhaseOids.length})" +
      (if (detail) orphanedPhaseOids.mkString(": ", ", ", "") else "") + "<br/>")
    if (go && orphanedPhaseOids.nonEmpty) {
      writer.println(s"${sp4}Deleting ${orphanedPhaseOids.length} orphaned phases<br/>")
      val phaseByOid = existingPhases.map(p => (p._id[ObjectId], p)).toMap
      orphanedPhaseOids.foreach(oid => PhaseApi.delete(phaseByOid(oid), request))
    }
    val missingPhaseOids: Seq[ObjectId] = expectedPhaseOids.filterNot(existingPhaseOids.toSet.contains)
    writer.println(s"${sp4}Missing phase Oids (${missingPhaseOids.length})" +
      (if (detail) missingPhaseOids.mkString(": ", ", ", "") else "") + "<br/>")
    writer.println(s"${sp2}EXIT deleteOrphanedPhases()<br/><br/>")
  }

  private def deleteOrphanedProcesses(writer: PrintWriter, go: Boolean, detail: Boolean): Unit = {
    writer.println(s"${sp2}ENTRY deleteOrphanedProcesses()<br/>")
    val phases: Seq[DynDoc] = BWMongoDB3.phases.find()
    val expectedProcessOids: Many[ObjectId] = phases.flatMap(_.process_ids[Many[ObjectId]]).distinct
    writer.println(s"${sp4}Expected process Oids (${expectedProcessOids.length})" +
      (if (detail) expectedProcessOids.mkString(": ", ", ", "") else "") + "<br/>")
    val existingProcesses: Seq[DynDoc] = BWMongoDB3.processes.find()
    val existingProcessOids: Seq[ObjectId] = existingProcesses.map(_._id[ObjectId])
    val orphanedProcessOids: Seq[ObjectId] = existingProcessOids.filterNot(expectedProcessOids.toSet.contains)
    writer.println(s"${sp4}Orphaned process Oids (${orphanedProcessOids.length})" +
      (if (detail) orphanedProcessOids.mkString(": ", ", ", "") else "") + "<br/>")
    if (go && orphanedProcessOids.nonEmpty) {
      writer.println(s"${sp4}Deleting ${orphanedProcessOids.length} orphaned processes<br/>")
      val processByOid = existingProcesses.map(p => (p._id[ObjectId], p)).toMap
      orphanedProcessOids.foreach(oid => ProcessApi.delete(processByOid(oid)) match {
        case Right(msg) => writer.println(s"Ok: $msg<br/>")
        case Left(msg) => writer.println(s"ERROR: $msg<br/>")
      })
    }
    val missingProcessOids: Seq[ObjectId] = expectedProcessOids.filterNot(existingProcessOids.toSet.contains)
    writer.println(s"${sp4}Missing process Oids (${missingProcessOids.length})" +
      (if (detail) missingProcessOids.mkString(": ", ", ", "") else "") + "<br/>")
    writer.println(s"${sp2}EXIT deleteOrphanedProcesses()<br/><br/>")
  }

  private def deleteOrphanedActivities(writer: PrintWriter, go: Boolean, detail: Boolean): Unit = {
    writer.println(s"${sp2}ENTRY deleteOrphanedActivities()<br/>")
    val processes: Seq[DynDoc] = BWMongoDB3.processes.find()
    val expectedActivityOids: Many[ObjectId] = processes.flatMap(_.activity_ids[Many[ObjectId]]).distinct
    writer.println(s"${sp4}Expected activity Oids (${expectedActivityOids.length})" +
      (if (detail) expectedActivityOids.mkString(": ", ", ", "") else "") + "<br/>")
    val existingActivities: Seq[DynDoc] = BWMongoDB3.tasks.find()
    val existingActivityOids: Seq[ObjectId] = existingActivities.map(_._id[ObjectId])
    val orphanedActivityOids: Seq[ObjectId] = existingActivityOids.filterNot(expectedActivityOids.toSet.contains)
    writer.println(s"${sp4}Orphaned activity Oids (${orphanedActivityOids.length})" +
      (if (detail) orphanedActivityOids.mkString(": ", ", ", "") else "") + "<br/>")
    if (go && orphanedActivityOids.nonEmpty) {
      writer.println(s"${sp4}Deleting ${orphanedActivityOids.length} orphaned activities<br/>")
      val deleteResult = BWMongoDB3.tasks.deleteMany(Map("_id" -> Map("$in" -> orphanedActivityOids)))
      if (deleteResult.getDeletedCount != orphanedActivityOids.length) {
        writer.println(s"${sp4}Deleted only ${deleteResult.getDeletedCount} orphaned activities<br/>")
      }
    }
    val missingActivityOids: Seq[ObjectId] = expectedActivityOids.filterNot(existingActivityOids.toSet.contains)
    writer.println(s"${sp4}Missing activity Oids (${missingActivityOids.length})" +
      (if (detail) missingActivityOids.mkString(": ", ", ", "") else "") + "<br/>")
    writer.println(s"${sp2}EXIT deleteOrphanedActivities()<br/><br/>")
  }

  private def deleteOrphanedDeliverables(writer: PrintWriter, go: Boolean, detail: Boolean): Unit = {
    writer.println(s"${sp2}ENTRY deleteOrphanedDeliverables()<br/>")
    val existingDeliverables: Seq[DynDoc] = BWMongoDB3.deliverables.find()
    val parentActivityOids: Seq[ObjectId] = existingDeliverables.map(_.activity_id[ObjectId]).distinct
    val existingParentActivities: Seq[DynDoc] = BWMongoDB3.tasks.find(Map("_id" -> Map($in -> parentActivityOids)))
    val existingParentActivityOids: Set[ObjectId] = existingParentActivities.map(_._id[ObjectId]).toSet
    val orphanedDeliverables = existingDeliverables.
        filterNot(d => existingParentActivityOids.contains(d.activity_id[ObjectId]))
    val orphanedDeliverableOids: Seq[ObjectId] = orphanedDeliverables.map(_._id[ObjectId])
    writer.println(s"${sp4}Orphaned deliverable Oids (${orphanedDeliverableOids.length})" +
      (if (detail) orphanedDeliverableOids.mkString(": ", ", ", "") else "") + "<br/>")
    if (go && orphanedDeliverables.nonEmpty) {
      writer.println(s"${sp4}Deleting ${orphanedDeliverables.length} orphaned deliverables<br/>")
      val deleteResult = BWMongoDB3.deliverables.deleteMany(Map("_id" -> Map("$in" -> orphanedDeliverableOids)))
      if (deleteResult.getDeletedCount != orphanedDeliverableOids.length) {
        writer.println(s"${sp4}Deleted only ${deleteResult.getDeletedCount} orphaned deliverables<br/>")
      }
    }
    writer.println(s"${sp2}EXIT deleteOrphanedDeliverables()<br/><br/>")
  }

  private def deleteOrphanedConstraints(writer: PrintWriter, go: Boolean, detail: Boolean): Unit = {
    writer.println(s"${sp2}ENTRY deleteOrphanedConstraints()<br/>")
    val existingDeliverables: Seq[DynDoc] = BWMongoDB3.deliverables.find()
    val deliverableOids: Seq[ObjectId] = existingDeliverables.map(_._id[ObjectId])
    val orphanedConstraints: Seq[DynDoc] = BWMongoDB3.constraints.find(Map($and -> Seq(
      Map("owner_deliverable_id" -> Map($not -> Map($in -> deliverableOids))),
      Map("constraint_id" -> Map($not -> Map($in -> deliverableOids)))
    )))
    val orphanedConstraintOids: Seq[ObjectId] = orphanedConstraints.map(_._id[ObjectId])
    writer.println(s"${sp4}Orphaned constraint Oids (${orphanedConstraintOids.length})" +
      (if (detail) orphanedConstraintOids.mkString(": ", ", ", "") else "") + "<br/>")
    if (go && orphanedConstraints.nonEmpty) {
      writer.println(s"${sp4}Deleting ${orphanedConstraints.length} orphaned constraints<br/>")
      val deleteResult = BWMongoDB3.constraints.deleteMany(Map("_id" -> Map("$in" -> orphanedConstraintOids)))
      if (deleteResult.getDeletedCount != orphanedConstraintOids.length) {
        writer.println(s"${sp4}Deleted only ${deleteResult.getDeletedCount} orphaned constraints<br/>")
      }
    }
    writer.println(s"${sp2}EXIT deleteOrphanedConstraints()<br/><br/>")
  }

  private def deleteOrphanedDocs(writer: PrintWriter, go: Boolean, detail: Boolean): Unit = {
    writer.println(s"${sp2}ENTRY deleteOrphanedDocs()<br/>")
    val projects: Seq[DynDoc] = BWMongoDB3.projects.find()
    val projectOids = projects.map(_._id[ObjectId])
    val projectOrphanedDocs: Seq[DynDoc] = BWMongoDB3.document_master.
        find(Map($and -> Seq(Map("project_id" -> Map($exists -> true)),
          Map("project_id" -> Map($not -> Map($in -> projectOids))))))
    val projectOrphanedDocOids = projectOrphanedDocs.map(_._id[ObjectId])
    writer.println(s"${sp4}Project-Orphaned doc Oids (${projectOrphanedDocOids.length})" +
      (if (detail) projectOrphanedDocOids.mkString(": ", ", ", "") else "") + "<br/>")
    val phases: Seq[DynDoc] = BWMongoDB3.phases.find()
    val phaseOids = phases.map(_._id[ObjectId])
    val phaseOrphanedDocs: Seq[DynDoc] = BWMongoDB3.document_master.
        find(Map($and -> Seq(Map("_id" -> Map($not -> Map($in -> projectOrphanedDocOids))),
        Map("phase_id" -> Map($exists -> true)), Map("phase_id" -> Map($not -> Map($in -> phaseOids))))))
    val phaseOrphanedDocOids = phaseOrphanedDocs.map(_._id[ObjectId])
    writer.println(s"${sp4}Phase-Orphaned doc Oids (${phaseOrphanedDocOids.length})" +
      (if (detail) phaseOrphanedDocOids.mkString(": ", ", ", "") else "") + "<br/>")
    val orphanedDocOids: Seq[ObjectId] = projectOrphanedDocOids ++ phaseOrphanedDocOids
    //writer.println(s"${sp4}ALL Orphaned doc Oids (${orphanedDocOids.length}): " +
    //  orphanedDocOids.mkString(", ") + "<br/>")
    if (go && orphanedDocOids.nonEmpty) {
      writer.println(s"${sp4}Deleting ${orphanedDocOids.length} orphaned docs<br/>")
      val deleteResult = BWMongoDB3.document_master.deleteMany(Map("_id" -> Map("$in" -> orphanedDocOids)))
      if (deleteResult.getDeletedCount != orphanedDocOids.length) {
        writer.println(s"${sp4}Deleted only ${deleteResult.getDeletedCount} orphaned docs<br/>")
      }
    }
    writer.println(s"${sp2}EXIT deleteOrphanedDocs()<br/><br/>")
  }

  private def deleteOrphanedProjectTags(writer: PrintWriter, go: Boolean, detail: Boolean): Unit = {
    writer.println(s"${sp2}ENTRY deleteOrphanedProjectTags()<br/>")
    val existingProjects: Seq[DynDoc] = BWMongoDB3.projects.find()
    val existingProjectOids = existingProjects.map(_._id[ObjectId])
    val orphanedProjectTags: Seq[DynDoc] = BWMongoDB3.project_tags.find(
        Map("project_id" -> Map($not -> Map($in -> existingProjectOids))))
    val orphanedProjectTagOids: Many[ObjectId] = orphanedProjectTags.map(_._id[ObjectId])
    writer.println(s"${sp4}Orphaned project_tags (${orphanedProjectTagOids.length})" +
      (if (detail) orphanedProjectTagOids.mkString(": ", ", ", "") else "") + "<br/>")
    if (go && orphanedProjectTagOids.nonEmpty) {
      writer.println(s"${sp4}Deleting ${orphanedProjectTagOids.length} project_tags<br/>")
      val deleteResult = BWMongoDB3.project_tags.deleteMany(Map("_id" -> Map("$in" -> orphanedProjectTagOids)))
      if (deleteResult.getDeletedCount != orphanedProjectTagOids.length) {
        writer.println(s"${sp4}Deleted only ${deleteResult.getDeletedCount} orphaned project_tags<br/>")
      }
    }
    writer.println(s"${sp2}EXIT deleteOrphanedProjectTags()<br/><br/>")
  }

  @unused
  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    response.setContentType("text/html")
    val writer = response.getWriter
    writer.println("<html><body><tt>")
    writer.println(s"ENTRY ${getClass.getName}:main()<br/><br/>")
    try {
      val user: DynDoc = getUser(request)
      if (!PersonApi.isBuildWhizAdmin(Right(user)) || user.first_name[String] != "Sanjay") {
        throw new IllegalArgumentException("Not permitted")
      }
      val (go, detail) = if (args.length == 0) {
        (false, false)
      } else {
        (args(0).contains("GO"), args(0).contains("DETAIL"))
      }
      deleteOrphanedFolderAccessGrants(writer, go, detail)
      deleteOrphanedPhases(request, writer, go, detail)
      deleteOrphanedProcesses(writer, go, detail)
      deleteOrphanedActivities(writer, go, detail)
      deleteOrphanedDeliverables(writer, go, detail)
      deleteOrphanedConstraints(writer, go, detail)
      deleteOrphanedDocs(writer, go, detail)
      deleteOrphanedProjectTags(writer, go, detail)
      writer.println(s"EXIT ${getClass.getName}:main()<br/>")
    } catch {
      case t: Throwable =>
        writer.println(s"${t.getClass.getName}(${t.getMessage})")
        writer.println(t.getStackTrace.map(_.toString).mkString("<br/>"))
    } finally {
      writer.println("</tt></body></html>")
      writer.flush()
    }
  }

}
