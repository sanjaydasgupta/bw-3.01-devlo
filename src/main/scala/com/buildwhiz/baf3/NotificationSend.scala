package com.buildwhiz.baf3

import com.buildwhiz.baf2.{PersonApi, ProjectApi}
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils3}
import org.bson.Document
import org.bson.types.ObjectId

import java.util.regex.Pattern
import java.util.{Calendar, TimeZone}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._

class NotificationSend extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val t0 = System.currentTimeMillis()
      val postDataString = getStreamData(request)
      BWLogger.log(getClass.getName, request.getMethod, s"POST-Data: $postDataString")
      val postDataObject: DynDoc = Document.parse(postDataString)
      val issueNbr = postDataObject.issue_no[String]
      val receivedMessage = postDataObject.message[String]
      val message = receivedMessage.split("Issue:").mkString(s"Issue: [$issueNbr]")
      val optProjectOid = postDataObject.get[String]("project_id").map(new ObjectId(_))
      val subject = postDataObject.get[String]("subject") match {
        case None => optProjectOid match {
          case Some(pOid) => s"Message from project '${ProjectApi.projectById(pOid).name[String]}'"
          case None => "Message"
        }
        case Some(sub) => sub
      }
      val userMessages: Seq[DynDoc] = postDataObject.user_ids[Any] match {
        case csv: String => csv.split(",").toSeq.map(uid => new ObjectId(uid.trim)).
            map(oid => Map("person_id" -> oid, "urgent" -> false))
        case docs: Many[Document@unchecked] =>
          docs
      }
      val currentMillis = System.currentTimeMillis()
      val timestamps = new Document("created", currentMillis)
      userMessages.foreach(uir => {
        uir.subject = subject
        uir.message = message
        val urgent = uir.urgent[Boolean]
        uir.sent = urgent
        if (!urgent) {
          val project = ProjectApi.projectById(new ObjectId(uir.project_id[String]))
          val timeZoneName = ProjectApi.timeZone(project)
          val timeZone = TimeZone.getTimeZone(timeZoneName)
          val timeZoneOffset = timeZone.getRawOffset
          uir.tz_offset = timeZoneOffset
        }
        uir.timestamps = timestamps
      })
      for ((uid: ObjectId, subject, body) <- NotificationSend.groups(userMessages.filter(_.urgent[Boolean]))) {
        NotificationSend.send(subject, body, Seq(uid))
      }
      val insertManyResult = BWMongoDB3.batched_notifications.insertMany(userMessages.map(_.asDoc).asJava)
      if (insertManyResult.getInsertedIds.size() != userMessages.length)
        throw new IllegalArgumentException(s"MongoDB update failed: $insertManyResult")
      response.getWriter.print(successJson())
      response.setContentType("application/json")
      val delay = System.currentTimeMillis() - t0
      val urgentCount = userMessages.count(_.urgent[Boolean])
      val nonUrgentCount = userMessages.length - urgentCount
      BWLogger.log(getClass.getName, request.getMethod,
          s"EXIT (time: $delay ms, urgent: $urgentCount, non-urgent: $nonUrgentCount) ", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object NotificationSend extends MailUtils3 {

  private def myTrim(str: String): String = {
    val pattern = Pattern.compile("[. ]*(.+)")

    def innerTrim(s2: String): String = {
      val matcher = pattern.matcher(s2)
      if (matcher.matches()) {
        matcher.group(1)
      } else {
        s2
      }
    }

    innerTrim(innerTrim(str).reverse).reverse
  }

  private def groups(notifications: Seq[DynDoc]): Seq[(ObjectId, String, String)] = {
    val pattern = "Project:|[, ]+Phase:|[, ]+Issue:|[, ]+Activity:|[, ]+Message:"
    notifications.foreach(n => {
      val messageParts = n.message[String].split(pattern).drop(1).map(myTrim)
      n.project_name = messageParts(0)
      n.phase_name = messageParts(1)
      n.issue_title = messageParts(2)
      n.activity_name = if (messageParts.length == 5) messageParts(3) else ""
      n.message_text = if (messageParts.length == 5) messageParts(4) else messageParts(3)
    })
    notifications.groupBy(n => (n.project_name[String], n.phase_name[String], n.person_id[String])).map(kv => {
      val rows = kv._2.map(msg => {
        val rawTitle = msg.issue_title[String]
        val title = rawTitle.splitAt(rawTitle.length - 17)._1
        val person = PersonApi.personById(new ObjectId(kv._1._3))
        val timeZone = TimeZone.getTimeZone(person.tz[String])
        val cal = Calendar.getInstance(timeZone)
        val timestamps: DynDoc = msg.timestamps[Document]
        cal.setTimeInMillis(timestamps.created[Long])
        val time = "%02d:%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND))
        s"""<tr><td>$time</td><td>$title</td><td>${msg.activity_name[String]}</td><td>${msg.message_text[String]}</td></tr>"""
      }).mkString
      val td = """<td align="center">"""
      val columnHeader = s"""<tr>${td}Time</td>${td}Issue</td>${td}Activity</td>${td}Message</td></tr>"""
      val html = s"""<html><table border="1">$columnHeader$rows</table></html>"""
      (new ObjectId(kv._1._3), s"Mozaik (Project: ${kv._1._1}, Phase: ${kv._1._2})", html)
    }).toSeq
  }

  private def sendPhaseEmails(phaseOid: ObjectId, minTime: Long, subject: String, allowedEmails: Seq[String]): Unit = {
    val htmlsAndEmails: Seq[(String, Seq[String])] = StatusMailer.htmlsAndEmails(phaseOid, minTime)
    val allEmails: Seq[String] = if (htmlsAndEmails.nonEmpty) {
      htmlsAndEmails.map(_._2).reduceLeft(_ ++ _).distinct
    } else {
      Seq.empty[String]
    }
    BWLogger.log(getClass.getName, "LOCAL",
      s"""INFO bulkSend() issue-reports exist for: ${allEmails.mkString(", ")}""")
    for (email <- allEmails if allowedEmails.contains(email)) {
      BWMongoDB3.persons.find(Map("emails" -> Map($elemMatch -> Map("type" -> "work", "email" -> email)))).headOption match {
        case Some(user) =>
          val userOid = user._id[ObjectId]
          val htmls = htmlsAndEmails.filter(_._2.contains(email)).map(_._1)
          BWLogger.log(getClass.getName, "LOCAL",
            s"INFO bulkSend() sending ${htmls.length} issue-reports to '$email' (_id: $userOid)")
          val fullHtml = htmls.mkString("<html>", "<br/>", "</html>")
          send(subject, fullHtml, Seq(userOid))
        case None =>
          BWLogger.log(getClass.getName, "LOCAL",
            s"ERROR bulkSend() NO user found for email '$email''")
      }
    }
  }

  def bulkSend(currentMillis: Long): Unit = {
    try {
//      def sendBatch(notifications: Seq[DynDoc]): Unit = {
//        BWLogger.log(getClass.getName, "LOCAL", "bulkSend:sendBatch()-ENTRY")
//        for ((uid: ObjectId, subject, body) <- groups(notifications)) {
//          send(subject, body, Seq(uid))
//        }
//        val batchIds = notifications.map(_._id[ObjectId])
//        val updateResult = BWMongoDB3.batched_notifications.updateMany(Map("_id" -> Map($in -> batchIds)),
//          Map($set -> Map("sent" -> true)))
//        if (updateResult.getMatchedCount != batchIds.length) {
//          BWLogger.log(getClass.getName, "bulkSend",
//            s"ERROR (Failed to update 'batched_notifications': $updateResult) ")
//        }
//        BWLogger.log(getClass.getName, "LOCAL", "bulkSend:sendBatch()-EXIT")
//      }

      val info: DynDoc = BWMongoDB3.instance_info.find().head
//      val allNotifications: Seq[DynDoc] = BWMongoDB3.batched_notifications.find(Map("sent" -> false))
      if (info.instance[String] == "www.buildwhiz.com") {
//        val when = 20 * 3600000L
//        val selectedNotifications: Seq[DynDoc] = allNotifications.map(n => {n.ms = (currentMillis + n.tz_offset[Int]) % 86400000L; n}).
//            filter(n => n.ms[Long] >= when && n.ms[Long] < when + 10000)
//        sendBatch(selectedNotifications)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("PST"))
        calendar.setTimeInMillis(currentMillis)
        val hours = calendar.get(Calendar.HOUR_OF_DAY)
        val minutes = calendar.get(Calendar.MINUTE)
        if (hours == 20 && minutes == 0) {
          val phaseOid = new ObjectId("635cf87a70c9ab15ee6a4006")
          val allowedEmails = Seq("sanjay.dasgupta@buildwhiz.com", "prabhas@buildwhiz.com", "prabhask@gmail.com",
            "sanjay_dasgupta@hotmail.com")
          val startTime = currentMillis - 86400000L
          sendPhaseEmails(phaseOid, startTime, "Mozaik [430 Forest/Operations] Status Update", allowedEmails)
        }
      } else {
//        val when = 1800000L
//        val selectedNotifications: Seq[DynDoc] = allNotifications.map(n => {n.ms = currentMillis % 3600000L; n}).
//          filter(n => n.ms[Long] >= when && n.ms[Long] < when + 10000)
//        sendBatch(selectedNotifications)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        calendar.setTimeInMillis(currentMillis)
        val hours = calendar.get(Calendar.HOUR_OF_DAY)
        val minutes = calendar.get(Calendar.MINUTE)
        if (minutes == 0) {
          val phaseOid = new ObjectId("64b11c027dc4d10231175db4")
          val allowedEmails = Seq("shubhankar.saha@viavitae.co.in", "sanjay.dasgupta@buildwhiz.com", "timir.das@viavitae.co.in")
          val startTime = currentMillis - 3600000L
          sendPhaseEmails(phaseOid, startTime, "Mozaik [Mozaik-Development/Development] Status Update", allowedEmails)
        }
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "bulkSend",
          s"ERROR (${t.getLocalizedMessage}) ")
    }
  }

  def send(subject: String, message: String, userOids: Seq[ObjectId]): Unit = {
    Future {
      sendMail(userOids, subject, message, None)
    }
  }
}


