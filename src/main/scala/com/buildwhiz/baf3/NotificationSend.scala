package com.buildwhiz.baf3

import com.buildwhiz.baf2.ProjectApi
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils3}
import org.bson.Document
import org.bson.types.ObjectId

import java.util.TimeZone
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
      BWLogger.log(getClass.getName, request.getMethod, s"POST-Data: postDataString")
      val postDataObject: DynDoc = Document.parse(postDataString)
      val message = postDataObject.message[String]
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

  private def groups(notifications: Seq[DynDoc]): Seq[(ObjectId, String, String)] = {
    val pattern = "Project:|[, ]+Phase:|[, ]+Issue:|[, ]+Activity:|[, ]+Message:"
    notifications.foreach(n => {
      val Array(_, project, phase, issue, activity, message) = n.message[String].
        split(pattern).map(_.trim)
      n.project_name = project
      n.phase_name = phase
      n.issue_title = issue
      n.activity_name = activity
      n.message_text = message
    })
    notifications.groupBy(n => (n.project_name[String], n.phase_name[String], n.person_id[String])).map(kv => {
      val rows = kv._2.map(msg => {
        s"""<tr><td>${msg.issue_title[String]}</td><td>${msg.activity_name[String]}</td><td>${msg.message_text[String]}</td></tr>"""
      }).mkString
      val columnHeader = """<tr><td align="center">Issue</td><td align="center">Activity</td><td align="center">Message</td></tr>"""
      val html = s"""<html><table border="1">$columnHeader$rows</table></html>"""
      (new ObjectId(kv._1._3), s"Mozaik (Project: ${kv._1._1}, Phase: ${kv._1._2})", html)
    }).toSeq
  }

  def bulkSend(currentMillis: Long): Unit = {
    try {
      def sendBatch(notifications: Seq[DynDoc]): Unit = {
        BWLogger.log(getClass.getName, "LOCAL", "bulkSend:sendBatch()-ENTRY")
        for ((uid: ObjectId, subject, body) <- groups(notifications)) {
          send(subject, body, Seq(uid))
        }
        val batchIds = notifications.map(_._id[ObjectId])
        val updateResult = BWMongoDB3.batched_notifications.updateMany(Map("_id" -> Map($in -> batchIds)),
          Map($set -> Map("sent" -> true)))
        if (updateResult.getMatchedCount != batchIds.length) {
          BWLogger.log(getClass.getName, "bulkSend",
            s"ERROR (Failed to update 'batched_notifications': $updateResult) ")
        }
        BWLogger.log(getClass.getName, "LOCAL", "bulkSend:sendBatch()-EXIT")
      }

      val info: DynDoc = BWMongoDB3.instance_info.find().head
      val allNotifications: Seq[DynDoc] = BWMongoDB3.batched_notifications.find(Map("sent" -> false))
      val selectedNotifications: Seq[DynDoc] = if (info.instance[String] == "www.buildwhiz.com") {
        val when = 20 * 3600000L
        allNotifications.map(n => {n.ms = (currentMillis + n.tz_offset[Int]) % 86400000L; n}).
            filter(n => n.ms[Long] >= when && n.ms[Long] < when + 10000)
      } else {
        val when = 1800000L
        allNotifications.map(n => {n.ms = currentMillis % 3600000L; n}).
          filter(n => n.ms[Long] >= when && n.ms[Long] < when + 10000)
      }
      sendBatch(selectedNotifications)
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


