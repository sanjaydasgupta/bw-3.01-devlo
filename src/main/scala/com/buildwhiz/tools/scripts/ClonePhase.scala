package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.{PersonApi, PhaseApi, ProcessApi}
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.HttpUtils
import org.bson.types.ObjectId
import org.bson.Document
import com.buildwhiz.baf3.ProcessAdd
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import scala.jdk.CollectionConverters._

object ClonePhase extends HttpUtils {

  private def realignTaskTeams(task: DynDoc, destPhase: DynDoc, output: String => Unit): Unit = {
    output(s"${getClass.getName}:realignTaskTeams() ENTRY<br/>")
    val oldTeamAssignments = task.team_assignments[Many[Document]]
    val newTeamAssignments: Many[Document] = oldTeamAssignments.map(teamAssignment => {
      val oldTeamOid = teamAssignment.team_id[ObjectId]
      val oldTeam = BWMongoDB3.teams.find(Map("_id" -> oldTeamOid)).head
      BWMongoDB3.teams.find(Map("phase_id" -> destPhase._id[ObjectId],
          "team_name" -> oldTeam.team_name[String])).headOption match {
        case Some(newTeam) =>
          teamAssignment.team_id = newTeam._id[ObjectId]
        case None =>
          output(s"""<font color="red">${getClass.getName}:realignTaskTeams() named team NOT FOUND: ${oldTeam.team_name[String]}</font><br/>""")
      }
      teamAssignment
    }).map(_.asDoc).asJava
    task.team_assignments = newTeamAssignments
    output(s"${getClass.getName}:realignTaskTeams() EXIT<br/>")
  }

  private def cloneOneProcess(srcProcess: DynDoc, destPhase: DynDoc, request: HttpServletRequest,
      output: String => Unit): Unit = {
    output(s"${getClass.getName}:cloneOneProcess() ENTRY<br/>")
    // create new template process
    val destPhaseManager = PhaseApi.phaseById(PhaseApi.managers(Right(destPhase)).head)
    val newProcessOid = ProcessAdd.addProcess(destPhaseManager, srcProcess.bpmn_name[String], srcProcess.name[String],
        destPhase._id[ObjectId], srcProcess.`type`[String], request)
    // copy extra fields from source process
    val newProcess = ProcessApi.processById(newProcessOid)
    val srcProcessCopy = new Document(srcProcess.asDoc)
    val srcProcessFields: Set[String] = srcProcessCopy.asDoc.keySet().toArray.map(_.asInstanceOf[String]).toSet
    for (srcField <- srcProcessFields) {
      if (newProcess.has(srcField)) {
        srcProcessCopy.remove(srcField)
      }
    }
    val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> newProcessOid), Map($set -> srcProcessCopy))
    if (updateResult.getModifiedCount != 1) {
      output(s"""<font color="red">${getClass.getName}:cloneOneProcess() update process-clone FAILED: ${srcProcess.name[String]}</font><br/>""")
    }
    // set team-assignments of each task
    val srcTasks: Seq[DynDoc] = BWMongoDB3.tasks.find(Map("_id" -> Map($in -> newProcess.activity_ids[Many[Document]])))
    srcTasks.foreach(tsk => realignTaskTeams(tsk, destPhase, output))
    // for each task replicate activities (deliverables)
    // re-align teams of each activities
    // clone constraint records, re-align activity-id values in constraints
    output(s"${getClass.getName}:cloneOneProcess() EXIT<br/>")
  }

  private def cloneProcesses(phaseSrc: DynDoc, phaseDest: DynDoc, go: Boolean, request: HttpServletRequest,
      output: String => Unit): Unit = {
    output(s"${getClass.getName}:cloneProcesses() ENTRY<br/>")
    val srcProcessOids = phaseSrc.process_ids[Many[Document]]
    val srcTemplateProcesses: Seq[DynDoc] = BWMongoDB3.processes.find(Map("_id" -> Map($in -> srcProcessOids),
      "type" -> "Template"))
    output(s"""<font color="green">${getClass.getName}:cloneProcesses() Source Template process names: ${srcTemplateProcesses.map(_.name[String]).mkString(", ")}</font><br/>""")
    val destProcessOids = phaseDest.process_ids[Many[Document]]
    val destTemplateProcesses: Seq[DynDoc] = BWMongoDB3.processes.find(Map("_id" -> Map($in -> destProcessOids),
      "type" -> "Template"))
    val destProcessNames = destTemplateProcesses.map(_.name[String]).toSet
    output(s"""<font color="green">${getClass.getName}:cloneProcesses() Dest Template process names: $destProcessNames</font><br/>""")
    val processesToCopy = srcTemplateProcesses.filterNot(stp => destProcessNames.contains(stp.name[String]))
    if (go && processesToCopy.nonEmpty) {
      for (templateProcess <- processesToCopy) {
        cloneOneProcess(templateProcess, phaseDest, request, output)
      }
    } else {
      output(s"""<font color="green">${getClass.getName}:cloneProcesses() EXITING - Nothing to do</font><br/><br/>""")
    }
    output(s"${getClass.getName}:cloneProcesses() EXIT<br/>")
  }

  private def cloneOneTeam(teamToClone: DynDoc, phaseDest: DynDoc, output: String => Unit): Unit = {
    output(s"${getClass.getName}:cloneOneTeam() ENTRY<br/>")
    teamToClone.remove("_id")
    val destPhaseOid = phaseDest._id[ObjectId]
    if (teamToClone.has("phase_id")) {
      teamToClone.phase_id = destPhaseOid.toString
    }
    val insertOneResult = BWMongoDB3.teams.insertOne(teamToClone.asDoc)
    val newTeamOid = insertOneResult.getInsertedId.asObjectId()
    val updatResult = BWMongoDB3.phases.updateOne(Map("_id" -> destPhaseOid),
      Map($push -> Map("team_assignments" -> new Document("team_id", newTeamOid))))
    if (updatResult.getModifiedCount == 1) {
      output(s"""<font color="green">${getClass.getName}:cloneOneTeam() cloned OK: ${teamToClone.team_name[String]}</font><br/>""")
    } else {
      output(s"""<font color="red">${getClass.getName}:cloneOneTeam() clone linking FAILED: ${teamToClone.team_name[String]}</font><br/>""")
    }
    output(s"${getClass.getName}:cloneOneTeam() EXIT<br/>")
  }

  private def cloneTeams(phaseSrc: DynDoc, phaseDest: DynDoc, go: Boolean, output: String => Unit): Unit = {
    output(s"${getClass.getName}:cloneTeams() ENTRY<br/><br/>")
    val sourceTeamOids: Seq[ObjectId] = phaseSrc.team_assignments[Many[Document]].map(_.team_id[ObjectId])
    output(s"""<font color="green">${getClass.getName}:cloneTeams() sourceTeam Oids: ${sourceTeamOids.mkString(", ")}</font><br/><br/>""")
    val sourceTeams: Seq[DynDoc] = BWMongoDB3.teams.find(Map("_id" -> Map($in -> sourceTeamOids)))
    output(s"""<font color="green">${getClass.getName}:cloneTeams() sourceTeam Names: ${sourceTeams.map(_.team_name[String]).mkString(", ")}</font><br/><br/>""")
    val destinationTeamOids: Seq[ObjectId] = phaseDest.team_assignments[Many[Document]].map(_.team_id[ObjectId])
    val destinationTeams: Seq[DynDoc] = BWMongoDB3.teams.find(Map("_id" -> Map($in -> destinationTeamOids)))
    val destinationTeamNames = destinationTeams.map(_.team_name[String]).toSet
    output(s"""<font color="green">${getClass.getName}:cloneTeams() destinationTeam Names: $destinationTeamNames</font><br/><br/>""")
    val teamsToCopy = sourceTeams.filterNot(t => destinationTeamNames.contains(t.team_name[String]))
    output(s"""<font color="green">${getClass.getName}:cloneTeams() teams to copy: ${teamsToCopy.map(_.team_name[String]).mkString(", ")}</font><br/><br/>""")
    if (go && teamsToCopy.nonEmpty) {
      for (teamToCopy <- teamsToCopy) {
        cloneOneTeam(teamToCopy, phaseDest, output)
      }
    } else {
      output(s"""<font color="green">${getClass.getName}:cloneTeams() EXITING - Nothing to do</font><br/><br/>""")
    }
    output(s"${getClass.getName}:cloneTeams() EXIT<br/>")
  }

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    response.setContentType("text/html")
    val writer = response.getWriter
    def output(s: String): Unit = writer.print(s)
    response.setContentType("text/html")
    output(s"<html><body>")
    output(s"${getClass.getName}:main() ENTRY<br/>")
    try {
      val user: DynDoc = getUser(request)
      if (!PersonApi.isBuildWhizAdmin(Right(user)) || user.first_name[String] != "Sanjay") {
        throw new IllegalArgumentException("Not permitted")
      }
      if (args.length >= 2) {
        val phaseSourceOid = new ObjectId(args(0))
        BWMongoDB3.phases.find(Map("_id" -> phaseSourceOid)).headOption match {
          case None =>
            output(s"""<font color="red">No such phase ID: '$phaseSourceOid'</font><br/>""")
          case Some(phaseSource) =>
            output(s"Found source phase: '${phaseSource.name[String]}'<br/>")
            val phaseDestOid = new ObjectId(args(1))
            BWMongoDB3.phases.find(Map("_id" -> phaseDestOid)).headOption match {
              case None =>
                output(s"""<font color="red">No such phase ID: '$phaseDestOid'</font><br/>""")
              case Some(phaseDest) =>
                output(s"Found dest phase: '${phaseDest.name[String]}'<br/>")
                val go: Boolean = args.length == 3 && args(2) == "GO"
                cloneTeams(phaseSource, phaseDest, go, output)
                cloneProcesses(phaseSource, phaseDest, go, request, output)
            }
        }
        output(s"${getClass.getName}:main() EXIT-OK<br/>")
      } else {
        output(s"""<font color="red">${getClass.getName}:main() EXIT-ERROR Usage: ${getClass.getName} src-phase-id dest-phase-id [GO]</font><br/>""")
      }
    } catch {
      case t: Throwable =>
        output(t.getStackTrace.map(_.toString).mkString("<br/>"))
    } finally {
      output("</body></html>")
    }
  }

}
