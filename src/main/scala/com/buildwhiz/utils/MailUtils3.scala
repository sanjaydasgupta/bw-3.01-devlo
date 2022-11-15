package com.buildwhiz.utils

import com.buildwhiz.baf2.PersonApi
import com.sendgrid.{Content, Email, Mail, Method, Request, SendGrid}
import org.bson.Document
import org.bson.types.ObjectId
import com.buildwhiz.infra.DynDoc._

import javax.servlet.http.HttpServletRequest
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import java.io.{File => javaFile, BufferedReader, FileReader}
import java.util.regex.Pattern

trait MailUtils3 {

  private def sendGridKey(): String = {
    val tomcatDir = new javaFile("server").listFiles.filter(_.getName.startsWith("apache-tomcat-")).head
    val doNotTouchFolder = new javaFile(tomcatDir, "webapps/bw-3.01/WEB-INF/classes/do-not-touch")
    if (!doNotTouchFolder.exists()) {
      val message = s"No such file: '${doNotTouchFolder.getAbsolutePath}'"
      BWLogger.log(getClass.getName, "getSendGridKey()", s"ERROR ($message)")
      throw new IllegalArgumentException(message)
    }
    val sendGridKeyFile = new javaFile(doNotTouchFolder, "sendgrid-key.txt")
    if (!sendGridKeyFile.exists()) {
      val message = s"No such file: '${sendGridKeyFile.getAbsolutePath}'"
      BWLogger.log(getClass.getName, "getSendGridKey()", s"ERROR ($message)")
      throw new IllegalArgumentException(message)
    }
    val keyFileReader = new BufferedReader(new FileReader(sendGridKeyFile))
        keyFileReader.readLine()
  }

  private def isHtml(text: String): Boolean = {
    val tagRe = Pattern.compile("</?[^>]+>")
    val parts = tagRe.split(text)
    parts.length > 1
  }

  def sendMail(recipientOids: Seq[ObjectId], subject: String, body: String, request: Option[HttpServletRequest]): Unit = {
    BWLogger.log(getClass.getName, "sendMail", s"ENTRY", request)
    val method = request match {
      case Some(req) => req.getMethod
      case None => "?"
    }
    try {
      val fromAddress = new Email("notifications@550of.com")
      val content = if (isHtml(body)) {
        new Content("text/html", body.replaceAll("\n", "<br/>"))
      } else {
        new Content("text/plain", body)
      }

      val sendGrid = new SendGrid(sendGridKey())
      BWLogger.log(getClass.getName, method, s"Init-sendMail(${recipientOids.length} destinations)", request)
      for (userOid <- recipientOids) {
        Future {
          try {
            BWLogger.log(getClass.getName, method, s"Begin-sendMail($userOid)", request)
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
            if (Seq(200, 202).contains(statusCode)) {
              BWLogger.log(getClass.getName, method, s"OK-sendMail($userOid)", request)
            } else {
              BWLogger.log(getClass.getName, method, s"Error-sendMail($statusCode-$statusBody $userOid)", request)
            }
          } catch {
            case t: Throwable =>
              BWLogger.log(getClass.getName, method, s"Error-sendMail: ${t.getClass.getName}(${t.getMessage})", request)
              t.printStackTrace(System.out)
          }
        }
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, method, s"Error-sendMail: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace(System.out)
    }
  }
}
