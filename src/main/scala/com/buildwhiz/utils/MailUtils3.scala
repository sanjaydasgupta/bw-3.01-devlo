package com.buildwhiz.utils

import com.buildwhiz.baf2.PersonApi
import org.bson.Document
import org.bson.types.ObjectId
import com.buildwhiz.infra.DynDoc._
import sibApi.TransactionalEmailsApi
import sibModel.{SendSmtpEmail, SendSmtpEmailReplyTo, SendSmtpEmailSender, SendSmtpEmailTo}

import javax.servlet.http.HttpServletRequest
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.io.{BufferedReader, FileReader, File => javaFile}
import java.util.regex.Pattern

trait MailUtils3 {

  private def fetchApiKey(): String = {
    val tomcatDir = new javaFile("server").listFiles.filter(_.getName.startsWith("apache-tomcat-")).head
    val doNotTouchFolder = new javaFile(tomcatDir, "webapps/bw-3.01/WEB-INF/classes/do-not-touch")
    if (!doNotTouchFolder.exists()) {
      val message = s"No such file: '${doNotTouchFolder.getAbsolutePath}'"
      BWLogger.log(getClass.getName, "fetchApiKey()", s"ERROR ($message)")
      throw new IllegalArgumentException(message)
    }
    val emailApiKeyFile = new javaFile(doNotTouchFolder, "sendinblue-key.txt")
    if (!emailApiKeyFile.exists()) {
      val message = s"No such file: '${emailApiKeyFile.getAbsolutePath}'"
      BWLogger.log(getClass.getName, "fetchApiKey()", s"ERROR ($message)")
      throw new IllegalArgumentException(message)
    }
    val keyFileReader = new BufferedReader(new FileReader(emailApiKeyFile))
        keyFileReader.readLine()
  }

  private def isHtml(text: String): Boolean = {
    val tagRe = Pattern.compile("</?[^>]+>")
    val parts = tagRe.split(text)
    parts.length > 1
  }

  def sendMail(recipientOids: Seq[ObjectId], subject: String, body: String, request: Option[HttpServletRequest]): Unit = {
    BWLogger.log(getClass.getName, "LOCAL", s"ENTRY-sendMail", request)
    val method = request match {
      case Some(req) => req.getMethod
      case None => "LOCAL"
    }
    try {
      BWLogger.log(getClass.getName, method, s"Init-sendMail(${recipientOids.length} destinations)", request)
      for (userOid <- recipientOids) {
        Future {
          try {
            BWLogger.log(getClass.getName, method, s"Begin-sendMail($userOid)", request)
            val user = PersonApi.personById(userOid)
            val workEmail = user.emails[Many[Document]].find(_.`type`[String] == "work").get.email[String]
            val api = new TransactionalEmailsApi()
            api.getApiClient.setApiKey(fetchApiKey())
            val sender = new SendSmtpEmailSender()
            sender.setName("BuildWhiz")
            sender.setEmail("info@430forest.com")
            val replyTo = new SendSmtpEmailReplyTo()
            replyTo.setEmail("noreply@430forest.com")
            replyTo.setName("NoReply")
            val to = new SendSmtpEmailTo()
            to.setEmail(workEmail)
            to.setName(PersonApi.fullName(user))
            val sendSmtpEmail = new SendSmtpEmail()
            sendSmtpEmail.setReplyTo(replyTo)
            sendSmtpEmail.setHtmlContent(if (isHtml(body)) body else s"<html>$body</html>")
            sendSmtpEmail.setTo(Seq(to))
            sendSmtpEmail.setSubject(subject)
            sendSmtpEmail.setSender(sender)
            val response = api.sendTransacEmail(sendSmtpEmail)
            BWLogger.log(getClass.getName, method, s"EXIT-sendMail-${response.getMessageId}", request)
          } catch {
            case t: Throwable =>
              BWLogger.log(getClass.getName, method, s"ERROR-sendMail: ${t.getClass.getName}(${t.getMessage})", request)
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
