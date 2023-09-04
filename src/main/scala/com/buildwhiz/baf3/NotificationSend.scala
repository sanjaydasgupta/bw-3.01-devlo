package com.buildwhiz.baf3

import com.buildwhiz.baf2.ProjectApi
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils3}
import org.bson.Document
import org.bson.types.ObjectId

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
      val timestamps = new Document("created", System.currentTimeMillis())
      userMessages.foreach(uir => {
        uir.subject = subject
        uir.message = message
        uir.sent = uir.urgent[Boolean]
        uir.timestamps = timestamps
      })
      for (urm <- userMessages.filter(_.urgent[Boolean])) {
        NotificationSend.send(urm.subject[String], urm.message[String], Seq(new ObjectId(urm.person_id[String])))
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

  def send(subject: String, message: String, userOids: Seq[ObjectId]): Unit = {
    Future {
      sendMail(userOids, subject, message, None)
    }
  }
}


