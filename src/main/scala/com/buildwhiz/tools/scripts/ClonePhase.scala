package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.HttpUtils
import org.bson.types.ObjectId
import org.bson.Document

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

object ClonePhase extends HttpUtils {

  private def cloneOneProcess(processToClone: DynDoc, phaseDest: DynDoc, output: String => Unit): Unit = {
    output(s"${getClass.getName}:cloneOneProcess() ENTRY<br/>")
    output(s"${getClass.getName}:cloneOneProcess() EXIT<br/>")
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
