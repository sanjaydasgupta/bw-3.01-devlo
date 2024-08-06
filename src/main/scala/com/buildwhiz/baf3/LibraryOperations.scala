package com.buildwhiz.baf3

import com.buildwhiz.baf2.{OrganizationApi, PersonApi, PhaseApi, ProcessApi}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB, BWMongoDB3, BWMongoDBLib, DynDoc}
import com.buildwhiz.utils.HttpUtils
import com.mongodb.client.model.UpdateOneModel
import org.bson.Document
import org.bson.types.{Decimal128, ObjectId}

import javax.servlet.http.HttpServletRequest
import scala.annotation.unused
import scala.jdk.CollectionConverters._

@unused
object LibraryOperations extends HttpUtils {

  val flagNames: Seq[String] = Seq(
    "activity", "activity_duration", "activity_contracted_budget", "activity_estimated_budget",
    "phase_estimated_budget", "task_duration", "task_estimated_budget", "export_as_private",
    "risk", "report", "workflow_template", "periodic_issue", "team_partner", "team_member", "zone")

  private val dummyMongoDbOid = new ObjectId("0" * 24)

  private type OUTPUT = String => Unit
  val margin: String = "&nbsp;" * 4

  private def replicatePersons(sourceDB: BWMongoDB, memberOids: Seq[ObjectId], destDB: BWMongoDB,
      output: LibraryOperations.OUTPUT): Unit = {
    output(s"""<br/>${getClass.getName}:replicatePersons() ENTRY<br/>""")
    val members: Seq[DynDoc] = sourceDB.persons.find(Map("_id" -> Map($in -> memberOids.asJava)))
    val insertResult = destDB.persons.insertMany(members.map(_.asDoc).asJava)
    val insertCount = insertResult.getInsertedIds.size()
    output(s"""<br/>${getClass.getName}:replicatePersons() Inserted $insertCount 'persons' records.<br/>""")
    output(s"""<br/>${getClass.getName}:replicatePersons() EXIT<br/>""")
  }

  private def cloneProcessSchedules(sourceDB: BWMongoDB, phaseSource: DynDoc, destDB: BWMongoDB, phaseDest: DynDoc,
      output: OUTPUT): Unit = {
    output(s"""<br/>${getClass.getName}:cloneProcessSchedules() ENTRY<br/>""")
    val schedules: Seq[DynDoc] = sourceDB.process_schedules.find(Map("phase_id" -> phaseSource._id[ObjectId]))
    val destPhaseOid = phaseDest._id[ObjectId]
    val parentProjectOid = if (destDB == BWMongoDBLib) {
      dummyMongoDbOid
    } else {
      PhaseApi.parentProject(destPhaseOid, destDB)._id[ObjectId]
    }
    for (schedule <- schedules) {
      schedule.phase_id = destPhaseOid
      schedule.project_id = parentProjectOid
    }
    output(s"""<br/>${getClass.getName}:cloneProcessSchedules() EXIT-OK<br/>""")
  }

  private def replicateConstraints(sourceDB: BWMongoDB, destDB: BWMongoDB, destProcess: DynDoc,
      output: OUTPUT): Unit = {
    output(s"""<br/>${getClass.getName}:replicateConstraints(${destProcess.name[String]}) ENTRY<br/>""")
    val destDeliverables: Seq[DynDoc] = destDB.deliverables.find(Map("process_id" -> destProcess._id[ObjectId]))
    if (destDeliverables.nonEmpty) {
      output(s"""${getClass.getName}:replicateConstraints()found ${destDeliverables.length} deliverables<br/>""")
      var maxCommonInstanceNo = {
        val aggPipe = Seq(new Document("$group", new Document("_id", null).
          append("max_common_instance_no", new Document("$max", "$common_instance_no"))))
        val aggResult: Seq[DynDoc] = destDB.constraints.aggregate(aggPipe)
        aggResult.headOption match {
          case Some(r) => r.max_common_instance_no[Int]
          case None => 0
        }
      }
      val srcDestDeliverablesDict = {
        destDeliverables.map(dd => {
          val migrationInfo: DynDoc = dd.migration_info[Document]
          val srcDeliverableOid = migrationInfo.src_deliverable_id[ObjectId]
          (srcDeliverableOid, dd._id[ObjectId])
        }).toMap
      }
      val srcDeliverableOids: Many[ObjectId] = srcDestDeliverablesDict.keys.toSeq
      val srcConstraints: Seq[DynDoc] = sourceDB.constraints.find(Map("owner_deliverable_id" ->
          Map($in -> srcDeliverableOids)))
      val goodBadSrcConstraints = srcConstraints.partition(sc =>
          srcDeliverableOids.contains(sc.owner_deliverable_id[ObjectId]) &&
          srcDeliverableOids.contains(sc.constraint_id[ObjectId]))
      if (goodBadSrcConstraints._2.nonEmpty) {
        output(s"""${getClass.getName}:replicateConstraints(): <font color="red">""" +
          s"""${goodBadSrcConstraints._2.length} of ${srcConstraints.length} constraints orphaned - ignored</font><br/>""")
      }
      if (goodBadSrcConstraints._1.nonEmpty) {
        output(s"""${getClass.getName}:replicateConstraints()found ${srcConstraints.length} constraints<br/>""")
        for (constraint <- goodBadSrcConstraints._1) {
          maxCommonInstanceNo += 1
          constraint.common_instance_no = maxCommonInstanceNo
          constraint.owner_deliverable_id = srcDestDeliverablesDict(constraint.owner_deliverable_id[ObjectId])
          val constraintOid = constraint.constraint_id[ObjectId]
          constraint.constraint_id = srcDestDeliverablesDict(constraintOid)
          constraint.remove("_id")
        }
        val result = destDB.constraints.insertMany(srcConstraints.map(_.asDoc).asJava)
        if (result.getInsertedIds.size() == srcConstraints.length) {
          val len = srcConstraints.length
          output(s"""${getClass.getName}:replicateConstraints()<font color="green"> Cloned $len constraints SUCCESS</font><br/>""")
        } else {
          output(s"""${getClass.getName}:replicateConstraints()<font color="red"> Cloning FAILED</font><br/>""")
        }
      } else {
        output(s"""${getClass.getName}:replicateConstraints()<font color="blue"> No constraints! EXITING </font><br/>""")
      }
    } else {
      output(s"""${getClass.getName}:replicateConstraints()<font color="blue"> No deliverables! EXITING </font><br/>""")
    }
    output(s"""${getClass.getName}:replicateConstraints(${destProcess.name[String]}) EXIT<br/>""")
  }

  private def replicateTaskDetails(sourceDB: BWMongoDB, srcProcess: DynDoc, destDB: BWMongoDB,
      destProcess: DynDoc, flags: Map[String, Boolean], output: OUTPUT): Unit = {
    output(s"""<br/>${getClass.getName}:replicateTaskDetails(${destProcess.name[String]}) ENTRY<br/>""")
    val srcTaskOids = srcProcess.activity_ids[Many[ObjectId]]
    val srcTasks: Seq[DynDoc] = sourceDB.tasks.find(Map("_id" -> Map($in -> srcTaskOids)))
    val srcTasksWithDuration: Map[String, Int] = if (flags("task_duration")) {
      def taskDuration(tsk: DynDoc): Int = {
        val durations: DynDoc = tsk.durations[Document]
        durations.likely[Int]
      }
      srcTasks.filter(taskDuration(_) != -1).
        map(task => (task.full_path_id[String], taskDuration(task))).toMap
    } else {
      Map.empty[String, Int]
    }
    val srcTasksWithBudget: Map[String, Decimal128] = if (flags("task_estimated_budget")) {
      srcTasks.filter(_.has("budget_estimated_plan")).
        map(task => (task.full_path_id[String], task.budget_estimated_plan[Decimal128])).toMap
    } else {
      Map.empty[String, Decimal128]
    }
    val budgetCount = srcTasksWithBudget.size
    val durationCount = srcTasksWithDuration.size
    if (budgetCount != 0 || durationCount != 0) {
      output(s"""${getClass.getName}:replicateTaskDetails(${destProcess.name[String]}) $budgetCount budgets, $durationCount durations found<br/>""")
      val allDestTaskOids = destProcess.activity_ids[Many[ObjectId]]
      val fullPathIds: Many[String] = (srcTasksWithBudget.keys.toSeq ++ srcTasksWithDuration.keys.toSeq).distinct
      val destTasks: Seq[DynDoc] = destDB.tasks.find(Map("_id" -> Map($in -> allDestTaskOids),
          "full_path_id" -> Map($in -> fullPathIds)))
      // if (destActivities.length != budgetCount) {
      //   throw new IllegalArgumentException(s"Expected $budgetCount tasks, found ${destActivities.length}")
      // }
      val bulkUpdateBuffer = destTasks.map(task => {
        val setterDoc = new Document()
        val fpId = task.full_path_id[String]
        if (srcTasksWithBudget.containsKey(fpId)) {
          setterDoc.append("budget_estimated_plan", srcTasksWithBudget(fpId))
        }
        if (srcTasksWithDuration.containsKey(fpId)) {
          setterDoc.append("durations.likely", srcTasksWithDuration(fpId))
        }
        new UpdateOneModel(new Document("_id", task._id[ObjectId]), new Document($set, setterDoc))
      })
      val result = destDB.tasks.bulkWrite(bulkUpdateBuffer)
      if (result.getModifiedCount != budgetCount) {
        throw new IllegalArgumentException(s"MongoDB update failed: $result")
      }
    } else {
      output(s"""${getClass.getName}:replicateTaskDetails(${destProcess.name[String]}) No budgets found!<br/>""")
    }
    output(s"""${getClass.getName}:replicateTaskDetails(${destProcess.name[String]}) EXIT<br/>""")
  }

  private def replicateDeliverables(sourceDB: BWMongoDB, srcProcess: DynDoc, destDB: BWMongoDB, destProcess: DynDoc,
      destPhase: DynDoc, teamsTable: Map[ObjectId, ObjectId], flags: Map[String, Boolean], output: OUTPUT): Unit = {
    output(s"""<br/>${getClass.getName}:replicateDeliverables(${destProcess.name[String]}) ENTRY<br/>""")
    val destPhaseOid = destPhase._id[ObjectId]
    val srcTaskOids = srcProcess.activity_ids[Many[ObjectId]]
    val srcTasks: Seq[DynDoc] = sourceDB.tasks.find(Map("_id" -> Map($in -> srcTaskOids)))
    val destTaskOids = destProcess.activity_ids[Many[ObjectId]]
    val destTasks: Seq[DynDoc] = destDB.tasks.find(Map("_id" -> Map($in -> destTaskOids)))
    val destTaskOidByBpmn: Map[String, ObjectId] = destTasks.
        map(dt => ("%s/%s".format(dt.bpmn_name_full[String], dt.bpmn_id[String]), dt._id[ObjectId])).toMap
    var maxCommonInstanceNo = {
      val aggPipe = Seq(new Document("$group", new Document("_id", null).append("max_common_instance_no",
        new Document("$max", "$common_instance_no"))))
      val aggResult: Seq[DynDoc] = destDB.deliverables.aggregate(aggPipe)
      aggResult.headOption match {
        case Some(r) => r.max_common_instance_no[Int]
        case None => 0
      }
    }
    val destProjectOid: ObjectId = try {
      val destProject = PhaseApi.parentProject(destPhaseOid, destDB)
      destProject._id[ObjectId]
    } catch {
      case _: Throwable =>
        dummyMongoDbOid
    }

    for (task <- srcTasks) {
      val taskOid = task._id[ObjectId]
      val srcDeliverables: Seq[DynDoc] = sourceDB.deliverables.find(Map("activity_id" -> taskOid))
      if (srcDeliverables.nonEmpty) {
        for (srcDeliverable <- srcDeliverables) {
          val migrationInfo: DynDoc = Map("src_deliverable_id" -> srcDeliverable._id[ObjectId],
              "bpmn_name_full" -> task.bpmn_name_full[String])
          srcDeliverable.migration_info = migrationInfo.asDoc
          srcDeliverable.project_id = destProjectOid
          srcDeliverable.phase_id = destPhaseOid
          srcDeliverable.process_id = destProcess._id[ObjectId]
          srcDeliverable.activity_id = destTaskOidByBpmn("%s/%s".format(task.bpmn_name_full[String], task.bpmn_id[String]))
          maxCommonInstanceNo += 1
          srcDeliverable.common_instance_no = maxCommonInstanceNo
          if (srcDeliverable.has("team_assignments")) {
            val teamAssignments: Seq[DynDoc] = srcDeliverable.team_assignments[Many[Document]]
            val newTeams = teamAssignments.filter(t => teamsTable.contains(t.team_id[ObjectId])).
              map(t => {t.team_id = teamsTable(t.team_id[ObjectId]); t.remove("contact_person_id"); t})
            srcDeliverable.team_assignments = newTeams.map(_.asDoc).asJava
          }
          if (srcDeliverable.has("budget_estimated") && !flags("activity_estimated_budget")) {
            srcDeliverable.remove("budget_estimated")
          }
          if (srcDeliverable.has("budget_contracted") && !flags("activity_contracted_budget")) {
            srcDeliverable.remove("budget_contracted")
          }
          if (!flags("activity_duration")) {
            srcDeliverable.duration = 0
          }
          srcDeliverable.remove("_id")
        }
        val result = destDB.deliverables.insertMany(srcDeliverables.map(_.asDoc).asJava)
        if (result.getInsertedIds.size() == srcDeliverables.length) {
          val len = srcDeliverables.length
          output(s"""${getClass.getName}:replicateDeliverables()<font color="green"> Cloned $len deliverables (task: ${task.name[String]}) SUCCESS</font><br/>""")
        } else {
          output(s"""${getClass.getName}:replicateDeliverables()<font color="red"> Cloning (task: ${task.name[String]}) FAILED</font><br/>""")
        }
      } else {
        output(s"""${getClass.getName}:replicateDeliverables(${task.full_path_name[String]})""" +
          s"""<font color="blue"> No deliverables! EXITING </font><br/>""")
      }
    }
    output(s"""${getClass.getName}:replicateDeliverables(${destProcess.name[String]}) EXIT<br/>""")
  }

  private def cloneOneProcess(sourceDB: BWMongoDB, srcProcess: DynDoc, destDB: BWMongoDB, destPhase: DynDoc,
      teamsTable: Map[ObjectId, ObjectId], request: HttpServletRequest, flags: Map[String, Boolean], output: OUTPUT):
      Unit = {
    output(s"<br/>${getClass.getName}:cloneOneProcess(${srcProcess.name[String]}) ENTRY<br/>")
    // create new template process
    val destPhaseManager = PersonApi.personById(PhaseApi.managers(Right(destPhase)).head)
    val newProcessOid = ProcessAdd.addProcess(destPhaseManager, srcProcess.bpmn_name[String], srcProcess.name[String],
        destPhase._id[ObjectId], srcProcess.`type`[String], destDB, request)
    output(s"""${getClass.getName}:cloneOneProcess()<font color="green"> ProcessAdd.addProcess SUCCESS</font><br/>""")
    // copy extra fields from source process
    val newProcess = ProcessApi.processById(newProcessOid, destDB)
    val srcProcessCopy = Document.parse(srcProcess.asDoc.toJson)
    val srcProcessFields = srcProcessCopy.asDoc.keySet().toArray.map(_.asInstanceOf[String]).toSeq
    for (srcField <- srcProcessFields) {
      if (newProcess.has(srcField)) {
        srcProcessCopy.remove(srcField)
      }
    }
    if (!srcProcessCopy.isEmpty) {
      val updateResult = destDB.processes.updateOne(Map("_id" -> newProcessOid), Map($set -> srcProcessCopy))
      if (updateResult.getModifiedCount == 1) {
        output(s"""${getClass.getName}:cloneOneProcess()<font color="green"> update process-clone SUCCESS</font><br/>""")
      } else {
        output(s"""${getClass.getName}:cloneOneProcess()<font color="red"> update process-clone FAILED</font><br/>""")
      }
    }
    // set default team-assignments of each task (NOT needed)
    // for each task replicate activities (deliverables) and realign teams
    replicateDeliverables(sourceDB, srcProcess, destDB, newProcess, destPhase, teamsTable, flags, output)
    // clone constraint records, re-align activity-id values in constraints
    replicateConstraints(sourceDB, destDB, newProcess, output)
    if (flags("task_estimated_budget") || flags("task_duration")) {
      replicateTaskDetails(sourceDB, srcProcess, destDB, newProcess, flags, output)
    }
    output(s"${getClass.getName}:cloneOneProcess(${srcProcess.name[String]}) EXIT<br/>")
  }

  private def getTeamsTable(sourceDB: BWMongoDB, phaseSrc: DynDoc, destDB: BWMongoDB, phaseDest: DynDoc):
      Map[ObjectId, ObjectId] = {
    val srcTeamOids: Many[ObjectId] = phaseSrc.team_assignments[Many[Document]].map(_.team_id[ObjectId])
    val srcAggrPipe: Many[Document] = Seq(
      new Document("$match", new Document("_id", new Document($in, srcTeamOids))),
      new Document("$project", new Document("team_name", 1).append("group", 1))
    )
    val srcTeams: Seq[DynDoc] = sourceDB.teams.aggregate(srcAggrPipe)

    val destTeamOids: Many[ObjectId] = phaseDest.team_assignments[Many[Document]].map(_.team_id[ObjectId])
    val destAggrPipe: Many[Document] = Seq(
      new Document("$match", new Document("_id", new Document($in, destTeamOids))),
      new Document("$project", new Document("team_name", 1).append("group", 1))
    )
    val destTeams: Seq[DynDoc] = destDB.teams.aggregate(destAggrPipe)
    val destTeamDict = destTeams.map(dt => ((dt.group[String], dt.team_name[String]), dt._id[ObjectId])).toMap
    val t2tMap: Map[ObjectId, ObjectId] = srcTeams.map(st => {
      (st._id[ObjectId], destTeamDict.getOrElse((st.group[String], st.team_name[String]), null))
    }).filterNot(_._2 == null).toMap
    t2tMap
  }

  private def cloneTemplateProcesses(sourceDB: BWMongoDB, phaseSrc: DynDoc, destDB: BWMongoDB, phaseDest: DynDoc,
      flags: Map[String, Boolean], teamsTable: Map[ObjectId, ObjectId], request: HttpServletRequest,
      output: OUTPUT): Unit = {
    output(s"<br/>${getClass.getName}:cloneProcesses(${phaseSrc.name[String]}) ENTRY<br/>")
    output(s"""${getClass.getName}:cloneProcesses()<font color="green"> Teams-Table size: ${teamsTable.size}</font><br/>""")
    val srcProcessOids = phaseSrc.process_ids[Many[Document]]
    val processesToClone: Seq[DynDoc] = sourceDB.processes.find(Map("_id" -> Map($in -> srcProcessOids),
      "type" -> "Template"))
    output(s"""${getClass.getName}:cloneProcesses()<font color="green"> Source process names: ${processesToClone.map(_.name[String]).mkString(", ")}</font><br/>""")
    val destProcessOids = phaseDest.process_ids[Many[Document]]
    val destProcesses: Seq[DynDoc] = destDB.processes.find(Map("_id" -> Map($in -> destProcessOids),
      "type" -> "Template"))
    val destProcessNames = destProcesses.map(_.name[String])
    output(s"""${getClass.getName}:cloneProcesses()<font color="green"> Existing Template process names: ${destProcessNames.mkString(", ")}</font><br/>""")
    val processesToCopy = processesToClone.filterNot(stp => destProcessNames.contains(stp.name[String]))
    output(s"""${getClass.getName}:cloneProcesses()<font color="green"> Processes to copy: ${processesToCopy.map(_.name[String]).mkString(", ")}</font><br/>""")
    if (processesToCopy.nonEmpty) {
      for (processToCopy <- processesToCopy) {
        cloneOneProcess(sourceDB, processToCopy, destDB, phaseDest, teamsTable, request, flags, output)
      }
    } else {
      output(s"""${getClass.getName}:cloneProcesses()<font color="green"> Nothing to do! EXITING</font><br/>""")
    }
    output(s"${getClass.getName}:cloneProcesses(${phaseSrc.name[String]}) EXIT<br/>")
  }

  private def cloneOneOrganization(orgToClone: DynDoc, destDB: BWMongoDB, output: OUTPUT): Unit = {
    output(s"<br/>${getClass.getName}:cloneOneOrganization(${orgToClone.name[String]}) ENTRY<br/>")
    val orgDoc = orgToClone.asDoc
    orgDoc.remove("_id")
    val updateResult = destDB.organizations.insertOne(orgDoc)
    if (updateResult.wasAcknowledged()) {
      output(s"""${getClass.getName}:cloneOneOrganization()<font color="green"> insert organization SUCCESS</font><br/>""")
    } else {
      output(s"""${getClass.getName}:cloneOneOrganization()<font color="red"> insert organization FAILED</font><br/>""")
    }
    output(s"<br/>${getClass.getName}:cloneOneOrganization(${orgToClone.name[String]}) EXIt<br/>")
  }

  private def cloneOneTeam(sourceDB: BWMongoDB, teamToClone: DynDoc, destDB: BWMongoDB, phaseDest: DynDoc,
      flags: Map[String, Boolean], output: OUTPUT): Unit = {
    output(s"<br/>${getClass.getName}:cloneOneTeam(${teamToClone.team_name[String]}) ENTRY<br/>")
    teamToClone.remove("_id")
    val destPhaseOid = phaseDest._id[ObjectId]
    if (flags("export_as_private")) {
      if (flags("team_partner") && teamToClone.has("organization_id")) {
        if (!OrganizationApi.exists(teamToClone.organization_id[ObjectId], destDB)) {
          val theOrg = OrganizationApi.organizationById(teamToClone.organization_id[ObjectId], sourceDB)
          cloneOneOrganization(theOrg, destDB, output)
        }
      }
      if (flags("team_member") && teamToClone.has("team_members")) {
        val teamMemberOids = teamToClone.team_members[Many[Document]].map(_.person_id[ObjectId])
        replicatePersons(sourceDB, teamMemberOids, destDB, output)
      }
    } else {
      if (!flags("team_partner") && teamToClone.has("organization_id")) {
        teamToClone.remove("organization_id")
      }
      if (!flags("team_member") && teamToClone.has("team_members")) {
        teamToClone.remove("team_members")
      }
    }
    val insertOneResult = destDB.teams.insertOne(teamToClone.asDoc)
    val newTeamOid = insertOneResult.getInsertedId.asObjectId()
    val updateResult = destDB.phases.updateOne(Map("_id" -> destPhaseOid),
      Map($push -> Map("team_assignments" -> new Document("team_id", newTeamOid))))
    if (updateResult.getModifiedCount == 1) {
      output(s"""${getClass.getName}:cloneOneTeam()<font color="green"> Cloning SUCCESS</font><br/>""")
    } else {
      output(s"""${getClass.getName}:cloneOneTeam()<font color="red"> Cloning FAILED</font><br/>""")
    }
    output(s"${getClass.getName}:cloneOneTeam(${teamToClone.team_name[String]}) EXIT<br/>")
  }

  private def cloneTeams(sourceDB: BWMongoDB, phaseSrc: DynDoc, destDB: BWMongoDB, phaseDest: DynDoc,
      flags: Map[String, Boolean], output: OUTPUT): Unit = {
    output(s"<br/>${getClass.getName}:cloneTeams(${phaseSrc.name[String]}) ENTRY<br/>")
    val sourceTeamOids: Seq[ObjectId] = phaseSrc.team_assignments[Many[Document]].map(_.team_id[ObjectId])
    // output(s"""<font color="green">${getClass.getName}:cloneTeams() sourceTeam Oids: ${sourceTeamOids.mkString(", ")}</font><br/>""")
    val sourceTeams: Seq[DynDoc] = sourceDB.teams.find(Map("_id" -> Map($in -> sourceTeamOids)))
    output(s"""${getClass.getName}:cloneTeams()<font color="green"> Source Team Names: ${sourceTeams.map(_.team_name[String]).mkString(", ")}</font><br/>""")
    val destinationTeamOids: Seq[ObjectId] = phaseDest.team_assignments[Many[Document]].map(_.team_id[ObjectId])
    val destinationTeams: Seq[DynDoc] = destDB.teams.find(Map("_id" -> Map($in -> destinationTeamOids)))
    val destinationTeamNames = destinationTeams.map(_.team_name[String]).toSet
    output(s"""${getClass.getName}:cloneTeams()<font color="green"> Existing Names: ${destinationTeamNames.mkString(", ")}</font><br/>""")
    val teamsToCopy = sourceTeams.filterNot(t => destinationTeamNames.contains(t.team_name[String]))
    output(s"""${getClass.getName}:cloneTeams()<font color="green"> Teams to copy: ${teamsToCopy.map(_.team_name[String]).mkString(", ")}</font><br/>""")
    if (teamsToCopy.nonEmpty) {
      for (teamToCopy <- teamsToCopy) {
        cloneOneTeam(sourceDB, teamToCopy, destDB, phaseDest, flags, output)
      }
    } else {
      output(s"""${getClass.getName}:cloneTeams()<font color="green"> EXITING - Nothing to do</font><br/>""")
    }
    output(s"${getClass.getName}:cloneTeams(${phaseSrc.name[String]}) EXIT<br/>")
  }

  def registerUser(user: DynDoc): Unit = {
    val info: DynDoc = BWMongoDB3.instance_info.find().head
    val instanceName = info.instance[String]
    val orgOid = user.organization_id[ObjectId]
    if (!OrganizationApi.exists(orgOid, BWMongoDBLib)) {
      val orgRecord = OrganizationApi.organizationById(orgOid, BWMongoDB3)
      orgRecord.instance_names = Seq(instanceName).asJava
      BWMongoDBLib.organizations.insertOne(orgRecord.asDoc)
    } else {
      BWMongoDBLib.organizations.updateOne(Map("_id" -> orgOid),
        Map($addToSet -> Map("instance_names" -> instanceName)))
    }
    val userOid = user._id[ObjectId]
    if (!PersonApi.exists(userOid, BWMongoDBLib)) {
      user.instance_names = Seq(instanceName).asJava
      BWMongoDBLib.persons.insertOne(user.asDoc)
    } else {
      BWMongoDBLib.persons.updateOne(Map("_id" -> userOid),
        Map($addToSet -> Map("instance_names" -> instanceName)))
    }
  }

  def exportPhase(phaseSourceOid: ObjectId, output: OUTPUT, description: String, flags: Map[String, Boolean],
      request: HttpServletRequest): Unit = {
    transportPhase(phaseSourceOid, None, output, Some(description), flags, request)
  }

  def importPhase(phaseSourceOid: ObjectId, projectDestOid: ObjectId, output: OUTPUT, flags: Map[String, Boolean],
      request: HttpServletRequest): Unit = {
    transportPhase(phaseSourceOid, Some(projectDestOid), output, None, flags, request)
  }

  def cleanLibrary(colName: String, output: OUTPUT): Unit = {
    output(s"""<br/>${getClass.getName}:cleanLibrary() ENTRY<br/>""")
    val collNames = BWMongoDBLib.collectionNames
    if (colName.matches("(?i)ALL")) {
      for (cn <- collNames) {
        output(s"Dropping collection: '$cn'<br/>")
        BWMongoDBLib(cn).drop()
      }
    } else {
      if (collNames.contains(colName)) {
        output(s"Dropping collection: '$colName'<br/>")
        BWMongoDBLib(colName).drop()
      } else {
        output(s"No such collection: '$colName'<br/>")
      }
    }
    output(s"""${getClass.getName}:cleanLibrary() EXIT<br/>""")
  }

  def listLibrary(output: OUTPUT): Unit = {
    output(s"""<br/>${getClass.getName}:listLibrary() ENTRY<br/>""")
    val collNames = BWMongoDBLib.collectionNames
    output(s"""${getClass.getName}:listLibrary()$margin Found ${collNames.length} collections<br/>""")
    for (cn <- collNames) {
      val count = BWMongoDBLib(cn).countDocuments()
      output(s"""${getClass.getName}:listLibrary()$margin$margin Collection '$cn' has $count records<br/>""")
      if (count > 0 && cn == "persons") {
        val persons: Seq[DynDoc] = BWMongoDBLib(cn).find()
        for (person <- persons) {
          val instNames = person.instance_names[Many[String]].mkString("[", ", ", "]")
          val fullName = PersonApi.fullName(person)
          output(s"""${getClass.getName}:listLibrary()$margin$margin$margin $fullName $instNames<br/>""")
        }
      }
      if (count > 0 && cn == "organizations") {
        val orgs: Seq[DynDoc] = BWMongoDBLib(cn).find()
        for (org <- orgs) {
          val instNames = org.instance_names[Many[String]].mkString("[", ", ", "]")
          output(s"""${getClass.getName}:listLibrary()$margin$margin$margin ${org.name[String]} $instNames<br/>""")
        }
      }
      if (count > 0 && cn == "phases") {
        val phases: Seq[DynDoc] = BWMongoDBLib(cn).find()
        for (phase <- phases) {
          val libraryInfo: DynDoc = phase.library_info[Document]
          val instanceName = libraryInfo.instance_name[String]
          val user = libraryInfo.user[String]
          val orgName = libraryInfo.original_partner[String]
          output(s"${getClass.getName}:listLibrary()$margin$margin$margin" +
            s""" ${phase.name[String]} (${phase._id[ObjectId]}) [$instanceName, $user, $orgName]<br/>""")
        }
      }
    }
    output(s"""${getClass.getName}:listLibrary() EXIT<br/>""")
  }

  private def transportPhase(phaseSourceOid: ObjectId, optProjectOid: Option[ObjectId], output: OUTPUT,
      optDescription: Option[String], flags: Map[String, Boolean], request: HttpServletRequest): Unit = {
    val (sourceDB, destDB) = if (optProjectOid.isDefined) {
      // Import from library
      (BWMongoDBLib, BWMongoDB3)
    } else {
      // Export to library
      (BWMongoDB3, BWMongoDBLib)
    }
    val phaseSource = sourceDB.phases.find(Map("_id" -> phaseSourceOid)).head
    val user: DynDoc = getUser(request)
    output(s"""<br/>${getClass.getName}:transportPhase(${phaseSource.name[String]} -> $optProjectOid) ENTRY<br/>""")
    val processSource: DynDoc = {
      val processOid = phaseSource.process_ids[Many[ObjectId]].head
      sourceDB.processes.find(Map("_id" -> processOid)).head
    }
    val (phaseDest, procDest) = {
      val (phaseOid, newProc) = PhaseAdd.addPhaseWithProcess(getUser(request), phaseSource.name[String], optProjectOid,
        phaseSource.description[String], Seq(user._id[ObjectId]), processSource.bpmn_name[String],
        processSource.name[String], destDB, flags, request)
      (destDB.phases.find(Map("_id" -> phaseOid)).head, newProc)
    }
    if (flags("phase_estimated_budget") && phaseSource.has("budget_estimated")) {
      destDB.phases.updateOne(Map("_id" -> phaseSourceOid),
          Map($set -> Map("budget_estimated" -> phaseSource.budget_estimated[Decimal128])))
    }
    val teamsTable = getTeamsTable(sourceDB, phaseSource, destDB, phaseDest)
    if (flags("task_estimated_budget") || flags("task_duration")) {
      replicateTaskDetails(sourceDB, processSource, destDB, procDest, flags, output)
    }
    if (flags("activity")) {
      replicateDeliverables(sourceDB, processSource, destDB, procDest, phaseDest, teamsTable, flags, output)
    }
    if (optProjectOid.isEmpty) {
      // For export only
      val info: DynDoc = BWMongoDB3.instance_info.find().head
      val instanceName = info.instance[String]
      val parentProject = PhaseApi.parentProject(phaseSourceOid)
      val partnerOid = parentProject.customer_organization_id[ObjectId]
      val originalProject = s"${parentProject.name[String]} (${parentProject._id[ObjectId]})"
      val partner = OrganizationApi.organizationById(partnerOid)
      val originalPartner = s"${partner.name[String]} ($partnerOid)"
      val libraryInfo: Document = Map("instance_name" -> instanceName, "description" -> optDescription.getOrElse("-"),
          "user" -> s"${PersonApi.fullName(user)} (${user._id[ObjectId]})", "timestamp" -> System.currentTimeMillis(),
          "original_partner" -> originalPartner, "original_project" -> originalProject,
          "private" -> flags("export_as_private"))
      destDB.phases.updateOne(Map("_id" -> phaseDest._id[ObjectId]),
        Map($set -> Map("library_info" -> libraryInfo)))
    } else {
      // For import only
      // destDB.phases.updateOne(Map("_id" -> phaseDest._id[ObjectId]),
      //   Map($set -> Map($unset -> "library_info")))
    }
    cloneTeams(sourceDB, phaseSource, destDB, phaseDest, flags, output)
    if (flags("workflow_template")) {
      cloneTemplateProcesses(sourceDB, phaseSource, destDB, phaseDest, flags, teamsTable, request, output)
    }
    if (flags("periodic_issue")) {
      cloneProcessSchedules(sourceDB, phaseSource, destDB, phaseDest, output)
    }
    output(s"""${getClass.getName}:transportPhase(${phaseSource.name[String]} -> $optProjectOid) EXIT<br/>""")
  }

}
