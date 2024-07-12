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

    def hasTaskDurations: Boolean = {
      val pipe: Many[Document] = Seq(
        s"""{$$match: {_id: {$$in: $procOidList}}}""",
        """{$unwind: "$activity_ids"}""",
        """{$lookup: {from: "tasks", localField: "activity_ids", foreignField: "_id", as: "tasks"}}""",
        """{$unwind: "$tasks"}""",
        """{$match: {"tasks.timestamps.likely": {$ne: -1}}}""",
        """{$group: {_id: null, count: {$sum: 1}}}"""
      ).map(Document.parse).asJava
      val tasks: Seq[DynDoc] = db.processes.aggregate(pipe)
      println("hasTaskDurations: " + tasks.map(_.asDoc.toJson).mkString("|"))
      tasks.nonEmpty
    }

    def teamsDetails: (Boolean, Boolean, Boolean) = {
      val teamOids: Many[ObjectId] = phase.team_assignments[Many[Document]].map(_.team_id[ObjectId])
      val teams: Seq[DynDoc] = db.teams.find(Map("_id" -> Map($in -> teamOids)))
      val teamsWithPartners = teams.filter(_.has("organization_id"))
      val teamsWithMembers = teamsWithPartners.filter(t => t.has("team_members") && t.team_members[Many[Document]].nonEmpty)
      (teams.nonEmpty, teamsWithPartners.nonEmpty, teamsWithMembers.nonEmpty)
    }

    val (teams, teamPartners, teamMembers) = teamsDetails

    val flags = Seq(
      "budgets" -> true, "teams" -> teams, "team_partners" -> teamPartners, "team_members" -> teamMembers,
      "task_durations" -> hasTaskDurations, "activities" -> true, "activity_durations" -> true, "risks" -> true,
      "workflow_templates" -> hasWorkflowTemplates, "zones" -> true, "spec_documents" -> true)

    flags
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
      val flags = LibraryContentsUtility.flags(db, phaseRecord)
      val trueFlags = flags.filter(_._2).map(_._1)
      response.getWriter.println(trueFlags.mkString("[", ",", "]"))
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