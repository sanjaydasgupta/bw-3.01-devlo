package com.buildwhiz.baf3

import com.buildwhiz.baf2.PhaseApi
import com.buildwhiz.infra.BWMongoDBLib._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB, BWMongoDB3, BWMongoDBLib, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.jdk.CollectionConverters.SeqHasAsJava

object LibraryContentsUtility {

  def flags(db: BWMongoDB, phase: DynDoc): Many[(String, Boolean)] = {
    val phaseOid = phase._id[ObjectId]
    val procOidList = phase.process_ids[Many[ObjectId]].map(oid => s"""ObjectId("$oid")""").mkString("[", ",", "]")

    def hasWorkflowTemplates: Boolean = {
      val pipe: Many[Document] = Seq(
        s"""{$$match: {_id: {$$in: $procOidList}, type: "Template"}}""",
        """{$group: {_id: null, count: {$sum: 1}}}"""
      ).map(Document.parse).asJava
      val processes: Seq[DynDoc] = db.processes.aggregate(pipe)
      println("hasWorkflowTemplates: " + processes.map(_.asDoc.toJson).mkString("|"))
      processes.nonEmpty
    }

    def taskDetails: (Boolean, Boolean) = {
      val pipe: Many[Document] = Seq(
        s"""{$$match: {_id: {$$in: $procOidList}}}""",
        """{$unwind: "$activity_ids"}""",
        """{$lookup: {from: "tasks", localField: "activity_ids", foreignField: "_id", as: "tasks"}}""",
        """{$unwind: "$tasks"}""",
        """{$replaceWith: "$tasks"}""",
        """{$project: {duration: {$ne: ["$timestamps.likely", -1]}, """ +
            """budget: {$ne: [{$ifNull: ["$budget_estimated_plan", 0]}, 0]}}}""",
        """{$match: {$expr: {$or: [{$eq: ["$duration", true]}, {$eq: ["$budget", true]}]}}}""",
        """{$group: {_id: null, count: {$sum: 1}, duration: {$push: "$duration"}, budget: {$push: "$budget"}}}""",
      ).map(Document.parse).asJava
      val tasks: Seq[DynDoc] = db.processes.aggregate(pipe)
      println("taskDetails: " + tasks.map(_.asDoc.toJson).mkString("|"))
      val tne = tasks.nonEmpty
      (tne && tasks.head.duration[Many[Boolean]].contains(true),
          tne && tasks.head.budget[Many[Boolean]].contains(true))
    }

    def teamsDetails: (Boolean, Boolean, Boolean) = {
      val teamsOidList = phase.team_assignments[Many[Document]].map(tid => s"""ObjectId("${tid.team_id[ObjectId]}")""").
        mkString("[", ",", "]")
      val pipe: Many[Document] = Seq(
        s"""{$$match: {_id: {$$in: $teamsOidList}}}""",
        """{$project: {orgs: {$ifNull: ["$organization_id", "X"]}, """ +
          """members: {$size: {$ifNull: ["$team_members", []]}}}}""",
        """{$project: {orgs: {$ne: ["$orgs", "X"]}, members: {$ne: ["$members", 0]}}}""",
        """{$group: {_id: null, count: {$sum: 1}, orgs: {$push: "$orgs"}, members: {$push: "$members"}}}""",
        """{$project: {count: true, orgs: {$in: [true, "$orgs"]}, members: {$in: [true, "$members"]}}}""",
      ).map(Document.parse).asJava
      val teams: Seq[DynDoc] = db.teams.aggregate(pipe)
      println("teamsDetails: " + teams.map(_.asDoc.toJson).mkString("|"))
      val tne = teams.nonEmpty
      (tne, tne && teams.head.orgs[Boolean], tne && teams.head.members[Boolean])
    }

    def activityDetails: (Boolean, Boolean, Boolean, Boolean) = {
      val pipe: Many[Document] = Seq(
        s"""{$$match: {phase_id: ObjectId("$phaseOid")}}""",
        """{$project: {has_duration: {$gt: ["$duration", 0]}, """ +
            """has_budget_estimated: {$ne: [{$ifNull: ["$budget_estimated", 0]}, 0]}""" +
            """has_budget_contracted: {$ne: [{$ifNull: ["$budget_contracted", 0]}, 0]}}}""",
        """{$group: {_id: null, count: {$sum: 1}, has_duration: {$push: "$has_duration"}, """ +
            """has_budget_estimated: {$push: "$has_budget_estimated"}, """ +
            """has_budget_contracted: {$push: "$has_budget_contracted"}}}""",
        """{$project: {count: true, has_duration: {$in: [true, "$has_duration"]}, """ +
            """has_budget_estimated: {$in: [true, "$has_budget_estimated"]}, """ +
            """has_budget_contracted: {$in: [true, "$has_budget_contracted"]}}}""",
      ).map(Document.parse).asJava
      val activities: Seq[DynDoc] = db.deliverables.aggregate(pipe)
      println("activityDetails: " + activities.map(_.asDoc.toJson).mkString("|"))
      val ane = activities.nonEmpty
      (ane, ane && activities.head.has_duration[Boolean], ane && activities.head.has_budget_estimated[Boolean],
            ane && activities.head.has_budget_contracted[Boolean])
    }

    val (hasTaskDurations, hasTaskBudgetEstimates) = taskDetails
    val (teams, teamPartners, teamMembers) = teamsDetails
    val (activities, activityDurations, activityBudgetsEstimated, activityBudgetsContracted) = activityDetails

    val flags = Seq(
      "phase_estimated_budget" -> true,
      // ...
      "task_duration" -> hasTaskDurations,
      "task_estimated_budget" -> hasTaskBudgetEstimates,
      "activity" -> activities,
      "activity_duration" -> activityDurations,
      "activity_estimated_budget" -> activityBudgetsEstimated,
      "activity_contracted_budget" -> activityBudgetsContracted,
      "team_partner" -> teamPartners,
      "team_member" -> teamMembers,
      // ...
      "workflow_template" -> hasWorkflowTemplates,
      "periodic_issue" -> true,
      // ...
      "report" -> true,
      "risk" -> true,
      "zone" -> true)

    flags
  }

  def trueFlagsList(db: BWMongoDB, phase: DynDoc): Many[String] = {
    flags(db, phase).filter(_._2).map(_._1).sorted
  }

}

class LibraryContentsUtility extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val phaseOid = new ObjectId(parameters("phase_id"))
      val direction = parameters("direction")
      val db: BWMongoDB = direction match {
        case "export" => BWMongoDB3
        case "import" => BWMongoDBLib
        case _ => throw new IllegalArgumentException(s"Bad direction value: '$direction'")
      }
      val phaseRecord: DynDoc = PhaseApi.phaseById(phaseOid)
      val flags = LibraryContentsUtility.trueFlagsList(db, phaseRecord)
      val flagsJson = flags.mkString("""{"flags":["""", """", """", """"]}""")
      response.getWriter.println(flagsJson)
      response.setContentType("application/json")
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}