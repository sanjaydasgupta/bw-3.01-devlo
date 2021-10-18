package com.buildwhiz.baf3

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import com.buildwhiz.slack.SlackApi
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
      val userOids = postDataObject.user_ids[String].split(",").map(s => new ObjectId(s.trim))
      NotificationSend.send(message, userOids, optProjectOid)
      response.getWriter.print(successJson())
      response.setContentType("application/json")
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

object NotificationSend {

  def send(message: String, userOids: Seq[ObjectId], optProjectOid: Option[ObjectId]): Unit = {
    Future {
      for (userOid <- userOids) {
        SlackApi.sendNotification(message, Right(userOid), optProjectOid)
      }
    }
  }
}


