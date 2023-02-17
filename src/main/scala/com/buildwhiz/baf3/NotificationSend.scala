package com.buildwhiz.baf3

import com.buildwhiz.baf2.ProjectApi
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils, MailUtils3}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class NotificationSend extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
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
      val userOids = postDataObject.user_ids[String].split(",").map(s => new ObjectId(s.trim))
      NotificationSend.send(subject, message, userOids.toSeq, optProjectOid)
      response.getWriter.print(successJson())
      response.setContentType("application/json")
      BWLogger.log(getClass.getName, request.getMethod, "EXIT", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object NotificationSend extends MailUtils3 {

  def send(subject: String, message: String, userOids: Seq[ObjectId], optProjectOid: Option[ObjectId]): Unit = {
    Future {
      sendMail(userOids, subject, message, None)
    }
  }
}


