package com.buildwhiz.baf3

import org.bson.Document
import org.bson.types.ObjectId
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.DateTimeUtils

import java.io.FileOutputStream
import java.util.{Calendar, Date, TimeZone}
import scala.collection.mutable
import scala.jdk.CollectionConverters._

object StatusMailer extends DateTimeUtils {

  private def getPhaseProgressInfo(phaseOid: ObjectId, startTime: Long): Seq[DynDoc] = {
    val pipeline: Seq[DynDoc] = Seq(
      Map("$match" -> Map("_id" -> phaseOid)),
      Map("$project" -> Map("phase_id" -> "$_id", "tz" -> true, "_id" -> false)),
      Map("$lookup" -> Map("from" -> "deliverables_progress_infos", "localField" -> "phase_id",
        "foreignField" -> "phase_id", "as" -> "dpi")),
      Map("$unwind" -> "$dpi"),
      Map("$project" -> Map("tz" -> true, "phase_id" -> true, "timestamp" -> "$dpi.timestamp",
        "process_id" -> "$dpi.process_id")),
      Map("$match" -> Map("timestamp" -> Map($gte -> startTime))),
      Map("$group" -> Map("_id" -> "$process_id", "tz" -> Map("$first" -> "$tz"), "phase_id" -> Map("$first" -> "$phase_id"))),
      Map("$project" -> Map("tz" -> true, "phase_id" -> true, "process_id" -> "$_id", "_id" -> false)),
      Map("$lookup" -> Map("from" -> "processes", "localField" -> "process_id", "foreignField" -> "_id",
        "as" -> "process")),
      Map("$unwind" -> "$process"),
      Map("$project" -> Map("tz" -> true, "phase_id" -> true, "process_id" -> true,
        "issue_no" -> "$process.issue_no", "issue_name" -> "$process.name")),
      Map("$lookup" -> Map("from" -> "deliverables_progress_infos", "localField" -> "process_id",
        "foreignField" -> "process_id", "as" -> "dpi")),
      Map("$unwind" -> "$dpi"),
      Map("$project" -> Map("tz" -> true, "phase_id" -> true, "process_id" -> true,
        "issue_no" -> true, "issue_name" -> true,
        "comment" -> "$dpi.comment", "system_comment" -> "$dpi.system_comment", "timestamp" -> "$dpi.timestamp",
        "deliverable_id" -> "$dpi.deliverable_id", "project_id" -> "$dpi.project_id")),
      Map("$lookup" -> Map("from" -> "deliverables", "localField" -> "deliverable_id", "foreignField" -> "_id",
        "as" -> "deliv")),
      Map("$unwind" -> Map("path" -> "$deliv", "preserveNullAndEmptyArrays" -> true)),
      Map("$project" -> Map("tz" -> true, "phase_id" -> true, "process_id" -> true,
        "issue_no" -> true, "issue_name" -> true,
        "comment" -> true, "system_comment" -> true, "timestamp" -> true,
        "deliverable_id" -> true, "project_id" -> true,
        "team_assignments" -> "$deliv.team_assignments", "deliverable_name" -> "$deliv.name",
        "status" -> "$deliv.status"))
    )
    val progressInfo: Seq[DynDoc] = BWMongoDB3.phases.aggregate(pipeline.map(_.asDoc).asJava)
    progressInfo
  }

  private def getEmails(teamOids: Seq[ObjectId]): Seq[String] = {
    val stages: Seq[DynDoc] = Seq(
      Map("$match" -> Map("_id" -> Map($in -> teamOids.asJava))),
      Map("$project" -> Map("team_members" -> true, "_id" -> false)),
      Map("$unwind" -> "$team_members"),
      Map("$project" -> Map("person_id" -> "$team_members.person_id")),
      Map("$lookup" -> Map("from" -> "persons", "localField" -> "person_id", "foreignField" -> "_id", "as" -> "persons")),
      Map("$unwind" -> "$persons"),
      Map("$replaceWith" -> "$persons"),
      Map("$project" -> Map("emails" -> true)),
      Map("$unwind" -> "$emails"),
      Map("$match" -> Map("emails.type" -> "work")),
      Map("$project" -> Map("email" -> "$emails.email"))
    )
    val emailRecords: Seq[DynDoc] = BWMongoDB3.teams.aggregate(stages.map(_.asDoc))
    emailRecords.map(_.email[String]).distinct
  }

  private def issueHtmlAndEmails(info: Seq[DynDoc]): (String, Seq[String]) = {
    val issueNo = "I-%06d".format(info.head.issue_no[Int])
    val issueNameRaw = info.head.issue_name[String]
    val issueName = if (issueNameRaw.reverse.matches("\\d{2}:\\d{2}-\\d{2}-\\d{2}-\\d{4}-.+")) {
      issueNameRaw.substring(0, issueNameRaw.length - 17)
    } else {
      issueNameRaw
    }
    val timeZoneName = info.head.tz[String]
    val timeZone = TimeZone.getTimeZone(timeZoneName)
    val calendar = Calendar.getInstance(timeZone)
    val teamAssignments: Seq[Seq[DynDoc]] = info.filter(_.has("team_assignments")).
      map(_.team_assignments[Many[Document]])
    val allTeamOids: Seq[Seq[ObjectId]] = teamAssignments.map(_.map(_.team_id[ObjectId]))
    val teamOids: Seq[ObjectId] = allTeamOids.length match {
      case 0 =>
        val process: DynDoc = BWMongoDB3.deliverables.find(Map("process_id" -> info.head.process_id[ObjectId])).head
        val teamsOids: Seq[ObjectId] = process.get[Many[Document]]("team_assignments") match {
          case Some(ta) => ta.map(_.team_id[ObjectId])
          case None => Seq.empty[ObjectId]
        }
        teamsOids
      case 1 => allTeamOids.head
      case _ => allTeamOids.reduceLeft(_ ++ _).distinct
    }
    val emails = getEmails(teamOids)
    val htmlBuffer = mutable.Buffer[String]()
    htmlBuffer.append(s"""<table border="1" width="100%">\n<tr align="center"><td align="center" colspan="4" bgcolor="PowderBlue"><b>[$issueNo] $issueName</b></td></tr>""")
    htmlBuffer.append(s"""<tr bgcolor="LightSteelBlue"><td align="center" width="5%">Date</td><td align="center" width="5%">Time</td><td align="center" width="25%">Activity</td><td align="center">Update</td></tr>""")
    for (i <- info.sortBy(_.timestamp[Long] * -1)) {
      val gmTime = i.timestamp[Long]
      val date = dateString2(gmTime, timeZoneName).replace(" ", "&nbsp;")
      calendar.setTime(new Date(gmTime))
      val time = "%02d:%02d:%02d".format(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND))
      val (activity, status) = if (i.has("deliverable_name")) (i.deliverable_name[String], i.status[String]) else ("-", "")
      val message = i.system_comment[String] + ". " + (if (i.has("comment")) i.comment[String] else "")
      val as = if (activity == "-") "-" else s"""$activity (${status.split("Deliverable-").last})"""
      htmlBuffer.append(s"""<tr><td align="center" width="5%">$date</td><td align="center" width="5%">$time</td><td align="center" width="25%">$as</td><td>$message</td></tr>""")
    }
    htmlBuffer.append("""</table>""")
    (htmlBuffer.mkString("\n"), emails)
  }

  def htmlsAndEmails(phaseOid: ObjectId, startTime: Long): Seq[(String, Seq[String])] = {
    val phaseProgressInfo = getPhaseProgressInfo(phaseOid, startTime)
    val infoByIssues: Seq[(Int, Seq[DynDoc])] = phaseProgressInfo.groupBy(_.issue_no[Int]).toSeq.
        sortBy(_._2.map(_.timestamp[Long]).max * -1)
    val htmlTablesAndEmails = infoByIssues.map(ibi => issueHtmlAndEmails(ibi._2))
    htmlTablesAndEmails
  }

  def main(args: Array[String]): Unit = {
//    val phaseInfo = getPhaseInfo(new ObjectId("64b11c027dc4d10231175db4"))
//    val (withDid, withoutDid) = phaseInfo.partition(_.has("deliverable_id"))
//    println(s"With deliv: ${withDid.length}, Without deliv: ${withoutDid.length}\n")
//    val tables = toHtmlTable(phaseInfo)
    val htmlsEmails = htmlsAndEmails(new ObjectId("64b11c027dc4d10231175db4"),
        System.currentTimeMillis() - (86400000L * 2))
    println(htmlsEmails.map(_._2))
    val tables = htmlsEmails.map(_._1).mkString("<br/><br/>")
    val of = new FileOutputStream("html.html")
    of.write("<html>".getBytes)
    of.write(tables.getBytes)
    of.write("</html>".getBytes)
    of.close()
    println(tables.length)
  }

}
