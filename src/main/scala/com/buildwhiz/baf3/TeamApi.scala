package com.buildwhiz.baf3

import com.buildwhiz.baf2.PhaseApi
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import org.bson.types.ObjectId
import org.bson.Document

object TeamApi {

  def teamsByIds(teamOids: Seq[ObjectId]): Seq[DynDoc] =
    BWMongoDB3.teams.find(Map("_id" -> Map($in -> teamOids)))

  def teamById(teamOid: ObjectId): DynDoc = BWMongoDB3.teams.find(Map("_id" -> teamOid)).head

  def teamsByMemberOid(memberOid: ObjectId): Seq[DynDoc] =
    BWMongoDB3.teams.find(Map("team_members" -> Map($elemMatch -> Map("person_id" -> memberOid))))

  def phasesByMemberOid(memberOid: ObjectId, optProjectOid: Option[ObjectId] = None): Seq[DynDoc] = {
    val teams = teamsByMemberOid(memberOid)
    val teamOids = teams.map(_._id[ObjectId])
    val teamPhases: Seq[DynDoc] = BWMongoDB3.phases.find(
        Map("team_assignments" -> Map($elemMatch -> Map("team_id" -> Map($in -> teamOids)))))
    optProjectOid match {
      case None => teamPhases
      case Some(projectOid) =>
          teamPhases.filter(phase => PhaseApi.parentProject(phase._id[ObjectId])._id[ObjectId] == projectOid)
    }
  }

  def memberOids(team: DynDoc): Seq[ObjectId] = team.get[Many[Document]]("team_members") match {
    case Some(members) => members.map(_.person_id[ObjectId])
    case None => Seq.empty[ObjectId]
  }

  def memberOids(teamOid: ObjectId): Seq[ObjectId] = memberOids(teamById(teamOid))

}
