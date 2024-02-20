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

  private def replicateDeliverables(srcProcess: DynDoc, destProcess: DynDoc, destPhase: DynDoc,
      teamsTable: Map[ObjectId, ObjectId], output: String => Unit): Unit = {
    output(s"${getClass.getName}:replicateDeliverables() ENTRY<br/>")
    val destPhaseOid = destPhase._id[ObjectId]
    val destProject = PhaseApi.parentProject(destPhaseOid)
    val srcTaskOids = srcProcess.activity_ids[Many[ObjectId]]
    val srcTasks: Seq[DynDoc] = BWMongoDB3.tasks.find(Map("_id" -> Map($in -> srcTaskOids)))
    val destTaskOids = destProcess.activity_ids[Many[ObjectId]]
    val destTasks: Seq[DynDoc] = BWMongoDB3.tasks.find(Map("_id" -> Map($in -> destTaskOids)))
    val destTaskOidByBpmn = destTasks.map(dt => (dt.bpmn_name_full[String], dt._id[ObjectId])).toMap
    val aggPipe = Seq(new Document("$group", new Document("_id", null).append("max_common_instance_no",
      new Document("$max", "$common_instance_no"))))
    val aggResult: Seq[DynDoc] = BWMongoDB3.deliverables.aggregate(aggPipe)
    var maxCommonInstanceNo = aggResult.head.max_common_instance_no[Int]
    for (task <- srcTasks) {
      val taskOid = task._id[ObjectId]
      val srcDeliverables: Seq[DynDoc] = BWMongoDB3.deliverables.find(Map("activity_id" -> taskOid))
      if (srcDeliverables.nonEmpty) {
        val migrationInfo: DynDoc = Map("src_task_id" -> taskOid, "bpmn_name_full" -> task.bpmn_name_full[String])
        for (taskDeliverable <- srcDeliverables) {
          taskDeliverable.migration_info = migrationInfo.asDoc
          taskDeliverable.project_id = destProject._id[ObjectId]
          taskDeliverable.phase_id = destPhaseOid
          taskDeliverable.process_id = destProcess._id[ObjectId]
          taskDeliverable.activity_id = destTaskOidByBpmn(task.bpmn_name_full[String])
          maxCommonInstanceNo += 1
          taskDeliverable.common_instance_no = maxCommonInstanceNo
          if (taskDeliverable.has("team_assignments")) {
            val teamAssignments: Seq[DynDoc] = taskDeliverable.team_assignments[Many[Document]]
            val newTeams = teamAssignments.filter(t => teamsTable.contains(t.team_id[ObjectId])).
              map(t => {t.team_id = teamsTable(t.team_id[ObjectId]); t.remove("contact_person_id"); t})
            taskDeliverable.team_assignments = newTeams.map(_.asDoc).asJava
          }
          taskDeliverable.remove("_id")
        }
        val result = BWMongoDB3.deliverables.insertMany(srcDeliverables.map(_.asDoc).asJava)
        if (result.getInsertedIds.size() == srcDeliverables.length) {
          output(s"""${getClass.getName}:replicateDeliverables()<font color="green"> ${task.bpmn_name_full[String]} SUCCESS</font><br/>""")
        } else {
          output(s"""${getClass.getName}:replicateDeliverables()<font color="red"> ${task.bpmn_name_full[String]} FAILED</font><br/>""")
        }
      } else {
        output(s"""${getClass.getName}:replicateDeliverables()<font color="green"> No deliverables to copy in: ${task.bpmn_name_full[String]} </font><br/>""")
      }
    }
    output(s"${getClass.getName}:replicateDeliverables() EXIT<br/>")
  }

  private def cloneOneProcess(srcProcess: DynDoc, destPhase: DynDoc, teamsTable: Map[ObjectId, ObjectId],
      request: HttpServletRequest, output: String => Unit): Unit = {
    output(s"${getClass.getName}:cloneOneProcess() ENTRY<br/>")
    // create new template process
    val destPhaseManager = PersonApi.personById(PhaseApi.managers(Right(destPhase)).head)
    val newProcessOid = ProcessAdd.addProcess(destPhaseManager, srcProcess.bpmn_name[String], srcProcess.name[String],
        destPhase._id[ObjectId], srcProcess.`type`[String], request)
    output(s"""${getClass.getName}:cloneOneProcess()<font color="green"> ProcessAdd.addProcess SUCCESS: ${srcProcess.name[String]}</font><br/>""")
    // copy extra fields from source process
    val newProcess = ProcessApi.processById(newProcessOid)
    val srcProcessCopy = Document.parse(srcProcess.asDoc.toJson)
    val srcProcessFields = srcProcessCopy.asDoc.keySet().toArray.map(_.asInstanceOf[String]).toSeq
    for (srcField <- srcProcessFields) {
      if (newProcess.has(srcField)) {
        srcProcessCopy.remove(srcField)
      }
    }
    if (!srcProcessCopy.isEmpty) {
      val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> newProcessOid), Map($set -> srcProcessCopy))
      if (updateResult.getModifiedCount == 1) {
        output(s"""${getClass.getName}:cloneOneProcess()<font color="green"> update process-clone SUCCESS: ${srcProcess.name[String]}</font><br/>""")
      } else {
        output(s"""${getClass.getName}:cloneOneProcess()<font color="red"> update process-clone FAILED: ${srcProcess.name[String]}</font><br/>""")
      }
    }
    // set default team-assignments of each task (NOT needed)
    // for each task replicate activities (deliverables) and realign teams
    replicateDeliverables(srcProcess, newProcess, destPhase, teamsTable, output)
    // clone constraint records, re-align activity-id values in constraints
    output(s"${getClass.getName}:cloneOneProcess() EXIT<br/><br/>")
  }

  private def getTeamsTable(phaseSrc: DynDoc, phaseDest: DynDoc): Map[ObjectId, ObjectId] = {
    val srcTeamOids: Many[ObjectId] = phaseSrc.team_assignments[Many[Document]].map(_.team_id[ObjectId])
    val srcAggrPipe: Many[Document] = Seq(
      new Document("$match", new Document("_id", new Document($in, srcTeamOids))),
      new Document("$project", new Document("team_name", 1).append("group", 1))
    )
    val srcTeams: Seq[DynDoc] = BWMongoDB3.teams.aggregate(srcAggrPipe)

    val destTeamOids: Many[ObjectId] = phaseDest.team_assignments[Many[Document]].map(_.team_id[ObjectId])
    val destAggrPipe: Many[Document] = Seq(
      new Document("$match", new Document("_id", new Document($in, destTeamOids))),
      new Document("$project", new Document("team_name", 1).append("group", 1))
    )
    val destTeams: Seq[DynDoc] = BWMongoDB3.teams.aggregate(destAggrPipe)
    val destTeamDict = destTeams.map(dt => ((dt.group[String], dt.team_name[String]), dt._id[ObjectId])).toMap
    val t2tMap: Map[ObjectId, ObjectId] = srcTeams.map(st => {
      (st._id[ObjectId], destTeamDict.getOrElse((st.group[String], st.team_name[String]), null))
    }).filterNot(_._2 == null).toMap
    t2tMap
  }

  private def cloneProcesses(phaseSrc: DynDoc, phaseDest: DynDoc, go: Boolean, request: HttpServletRequest,
      output: String => Unit): Unit = {
    output(s"${getClass.getName}:cloneProcesses() ENTRY<br/>")
    val teamsTable = getTeamsTable(phaseSrc, phaseDest)
    output(s"""${getClass.getName}:cloneProcesses()<font color="green"> Teams-Table size: ${teamsTable.size}</font><br/><br/>""")
    val srcProcessOids = phaseSrc.process_ids[Many[Document]]
    val srcTemplateProcesses: Seq[DynDoc] = BWMongoDB3.processes.find(Map("_id" -> Map($in -> srcProcessOids),
      "type" -> "Template"))
    output(s"""${getClass.getName}:cloneProcesses()<font color="green"> Source Template process names: ${srcTemplateProcesses.map(_.name[String]).mkString(", ")}</font><br/><br/>""")
    val destProcessOids = phaseDest.process_ids[Many[Document]]
    val destTemplateProcesses: Seq[DynDoc] = BWMongoDB3.processes.find(Map("_id" -> Map($in -> destProcessOids),
      "type" -> "Template"))
    val destProcessNames = destTemplateProcesses.map(_.name[String])
    output(s"""${getClass.getName}:cloneProcesses()<font color="green"> Dest Template process names: $destProcessNames</font><br/><br/>""")
    val processesToCopy = srcTemplateProcesses.filterNot(stp => destProcessNames.contains(stp.name[String]))
    output(s"""${getClass.getName}:cloneProcesses()<font color="green"> Processes to copy: ${processesToCopy.map(_.name[String])}</font><br/><br/>""")
    if (go && processesToCopy.nonEmpty) {
      for (templateProcess <- processesToCopy) {
        cloneOneProcess(templateProcess, phaseDest, teamsTable, request, output)
      }
    } else {
      output(s"""${getClass.getName}:cloneProcesses()<font color="green"> EXITING - Nothing to do</font><br/><br/>""")
    }
    output(s"${getClass.getName}:cloneProcesses() EXIT<br/><br/>")
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
      output(s"""${getClass.getName}:cloneOneTeam()<font color="green"> cloned OK: ${teamToClone.team_name[String]}</font><br/>""")
    } else {
      output(s"""${getClass.getName}:cloneOneTeam()<font color="red"> clone linking FAILED: ${teamToClone.team_name[String]}</font><br/>""")
    }
    output(s"${getClass.getName}:cloneOneTeam() EXIT<br/><br/>")
  }

  private def cloneTeams(phaseSrc: DynDoc, phaseDest: DynDoc, go: Boolean, output: String => Unit): Unit = {
    output(s"${getClass.getName}:cloneTeams() ENTRY<br/><br/>")
    val sourceTeamOids: Seq[ObjectId] = phaseSrc.team_assignments[Many[Document]].map(_.team_id[ObjectId])
    // output(s"""<font color="green">${getClass.getName}:cloneTeams() sourceTeam Oids: ${sourceTeamOids.mkString(", ")}</font><br/><br/>""")
    val sourceTeams: Seq[DynDoc] = BWMongoDB3.teams.find(Map("_id" -> Map($in -> sourceTeamOids)))
    output(s"""${getClass.getName}:cloneTeams()<font color="green"> sourceTeam Names: ${sourceTeams.map(_.team_name[String]).mkString(", ")}</font><br/><br/>""")
    val destinationTeamOids: Seq[ObjectId] = phaseDest.team_assignments[Many[Document]].map(_.team_id[ObjectId])
    val destinationTeams: Seq[DynDoc] = BWMongoDB3.teams.find(Map("_id" -> Map($in -> destinationTeamOids)))
    val destinationTeamNames = destinationTeams.map(_.team_name[String]).toSet
    output(s"""${getClass.getName}:cloneTeams()<font color="green"> destinationTeam Names: $destinationTeamNames</font><br/><br/>""")
    val teamsToCopy = sourceTeams.filterNot(t => destinationTeamNames.contains(t.team_name[String]))
    output(s"""${getClass.getName}:cloneTeams()<font color="green"> teams to copy: ${teamsToCopy.map(_.team_name[String]).mkString(", ")}</font><br/><br/>""")
    if (go && teamsToCopy.nonEmpty) {
      for (teamToCopy <- teamsToCopy) {
        cloneOneTeam(teamToCopy, phaseDest, output)
      }
    } else {
      output(s"""${getClass.getName}:cloneTeams()<font color="green"> EXITING - Nothing to do</font><br/><br/>""")
    }
    output(s"${getClass.getName}:cloneTeams() EXIT<br/><br/>")
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
        output("%s(%s)<br/>".format(t.getClass.getName, t.getMessage))
        output(t.getStackTrace.map(_.toString).mkString("<br/>"))
    } finally {
      output("</body></html>")
    }
  }

}
