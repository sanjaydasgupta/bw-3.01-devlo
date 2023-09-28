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

  private def getPhaseInfo(phaseOid: ObjectId): Seq[DynDoc] = {
    val pipeline: Seq[DynDoc] = Seq(
      Map("$match" -> Map("_id" -> phaseOid)),
      Map("$project" -> Map("process_ids" -> true, "tz" -> true, "_id" -> false)),
      Map("$unwind" -> "$process_ids"),
      Map("$lookup" -> Map("from" -> "processes", "localField" -> "process_ids", "foreignField" -> "_id", "as" -> "process")),
      Map("$unwind" -> "$process"),
      Map("$match" -> Map("process.type" -> "Transient", "process.status" -> Map("$ne" -> "completed"))),
      Map("$lookup" -> Map("from" -> "deliverables_progress_infos", "localField" -> "process._id",
          "foreignField" -> "process_id", "as" -> "dpi")),
      Map("$unwind" -> "$dpi"),
      Map("$project" -> Map("process_id" -> "$process._id", "issue_name" -> "$process.name", "tz" -> "$tz",
        "issue_no" -> "$process.issue_no", "comment" -> "$dpi.comment", "system_comment" -> "$dpi.system_comment",
        "timestamp" -> "$dpi.timestamp", "deliverable_id" -> "$dpi.deliverable_id", "project_id" -> "$dpi.project_id",
        "phase_id" -> "$dpi.phase_id")),
      Map("$lookup" -> Map("from" -> "deliverables", "localField" -> "deliverable_id", "foreignField" -> "_id",
        "as" -> "deliv")),
      Map("$unwind" -> Map("path" -> "$deliv", "preserveNullAndEmptyArrays" -> true)),
      Map("$project" -> Map("process_id" -> true, "issue_name" -> true, "issue_no" -> true, "phase_id" -> true,
        "project_id" -> true, "system_comment" -> true, "comment" -> true, "timestamp" -> true, "tz" -> true,
        "deliverable_id" -> true, "team_assignments" -> "$deliv.team_assignments", "deliverable_name" -> "$deliv.name",
        "status" -> "$deliv.status"))
    )
    val phaseInfo: Seq[DynDoc] = BWMongoDB3.phases.aggregate(pipeline.map(_.asDoc).asJava)
    phaseInfo
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
    val issueName = info.head.issue_name[String]
    val timeZoneName = info.head.tz[String]
    val timeZone = TimeZone.getTimeZone(timeZoneName)
    val calendar = Calendar.getInstance(timeZone)
    val teamAssignments: Seq[Seq[DynDoc]] = info.filter(_.has("team_assignments")).
      map(_.team_assignments[Many[Document]])
    val teamOids: Seq[ObjectId] = teamAssignments.map(_.map(_.team_id[ObjectId])).reduceLeft(_ ++ _).distinct
    val emails = getEmails(teamOids)
    val htmlBuffer = mutable.Buffer[String]()
    htmlBuffer.append(s"""<table border="1" width="100%">\n<tr align="center"><td align="center" colspan="4" bgcolor="orange"><b>[$issueNo] $issueName</b></td></tr>""")
    htmlBuffer.append(s"""<tr bgcolor="yellow"><td align="center" width="5%">Date</td><td align="center" width="5%">Time</td><td align="center">Activity</td><td align="center">Message</td></tr>""")
    for (i <- info.sortBy(_.timestamp[Long] * -1)) {
      val gmTime = i.timestamp[Long]
      val date = dateString2(gmTime, timeZoneName)
      calendar.setTime(new Date(gmTime))
      val time = "%02d:%02d:%02d".format(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND))
      val (activity, status) = if (i.has("deliverable_name")) (i.deliverable_name[String], i.status[String]) else ("-", "")
      val message = i.system_comment[String] + ". " + (if (i.has("comment")) i.comment[String] else "")
      val as = if (activity == "-") "-" else s"""$activity (${status.split("Deliverable-").last})"""
      htmlBuffer.append(s"""<tr><td align="center" width="5%">$date</td><td align="center" width="5%">$time</td><td align="center">$as</td><td>$message</td></tr>""")
    }
    htmlBuffer.append("""</table>""")
    (htmlBuffer.mkString("\n"), emails)
  }

  def htmlsAndEmails(phaseOid: ObjectId): Seq[(String, Seq[String])] = {
    val phaseInfo = getPhaseInfo(phaseOid)
    val infoByIssues: Seq[(Int, Seq[DynDoc])] = phaseInfo.groupBy(_.issue_no[Int]).toSeq.sortBy(_._1 * -1)
    val htmlTablesAndEmails = infoByIssues.map(ibi => issueHtmlAndEmails(ibi._2))
    htmlTablesAndEmails
  }

  private def toHtmlTable(issueInfo: Seq[DynDoc]): String = {
    val infoByIssues: Seq[(Int, Seq[DynDoc])] = issueInfo.groupBy(_.issue_no[Int]).toSeq.sortBy(_._1 * -1)
    val htmlTablesAndEmails = infoByIssues.map(ibi => issueHtmlAndEmails(ibi._2))
    htmlTablesAndEmails.map(p => s"""<p>${p._2.mkString(", ")}</p>${p._1}""").mkString("<br/><br/>\n")
  }

  def main(args: Array[String]): Unit = {
//    val phaseInfo = getPhaseInfo(new ObjectId("64b11c027dc4d10231175db4"))
//    val (withDid, withoutDid) = phaseInfo.partition(_.has("deliverable_id"))
//    println(s"With deliv: ${withDid.length}, Without deliv: ${withoutDid.length}\n")
//    val tables = toHtmlTable(phaseInfo)
val htmlsEmails = htmlsAndEmails(new ObjectId("64b11c027dc4d10231175db4"))
    val tables = htmlsEmails.map(_._1).mkString("<br/><br/>")
    val of = new FileOutputStream("html.html")
    of.write("<html>".getBytes)
    of.write(tables.getBytes)
    of.write("</html>".getBytes)
    of.close()
    println(tables.length)
  }

}
