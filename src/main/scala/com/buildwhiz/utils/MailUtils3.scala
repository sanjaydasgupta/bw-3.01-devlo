package com.buildwhiz.utils

import com.buildwhiz.baf2.PersonApi
import com.sendgrid.{Content, Email, Mail, Method, Request, SendGrid}
import org.bson.Document
import org.bson.types.ObjectId
import com.buildwhiz.infra.DynDoc._

import javax.servlet.http.HttpServletRequest
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait MailUtils3 {
  def sendMail(recipientOids: Seq[ObjectId], subject: String, body: String, request: Option[HttpServletRequest]): Unit = {
    BWLogger.log(getClass.getName, "sendMail", s"ENTRY", request)
    try {
      val fromAddress = new Email("notifications@550of.com")
      val content = new Content("text/plain", body)
      val sendGrid = new SendGrid(System.getenv("sendgrid_api_key"))
      for (userOid <- recipientOids) {
        Future {
          val user = PersonApi.personById(userOid)
          val workEmail = user.emails[Many[Document]].find(_.`type`[String] == "work").get.email[String]
          val toAddress = new Email(workEmail)
          val mail = new Mail(fromAddress, subject, toAddress, content)
          val sendGridRequest = new Request()
          sendGridRequest.setMethod(Method.POST)
          sendGridRequest.setEndpoint("mail/send")
          sendGridRequest.setBody(mail.build())
          val response = sendGrid.api(sendGridRequest)
          val statusCode = response.getStatusCode
          val statusBody = response.getBody
          //val statusHeaders = response.getHeaders
          if (statusCode == 200) {
            BWLogger.log(getClass.getName, "sendMail", s"EXIT-OK", request)
          } else {
            BWLogger.log(getClass.getName, "sendMail", s"WARN-($statusCode)-$statusBody", request)
          }
        }
      }
    } catch {
      case t: Throwable =>
        t.printStackTrace(System.out)
    }
  }
}
