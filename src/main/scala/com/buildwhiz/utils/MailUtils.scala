package com.buildwhiz.utils

import java.util.Properties
import javax.mail._
import javax.mail.internet.{InternetAddress, MimeBodyPart, MimeMessage, MimeMultipart}
import javax.servlet.http.HttpServletRequest

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import org.bson.Document
import org.bson.types.ObjectId

import scala.concurrent.Future
import scala.util.Failure

trait MailUtils {

  val emailSignature: String =
    """
      |Please do not reply to this email, this mailbox is not monitored.
      |
      |The 430 Forest project""".stripMargin

  def sendMail(recipientOid: ObjectId, subject: String, body: String, request: Option[HttpServletRequest]): Unit =
    sendMail(Seq(recipientOid), subject, body, request)

  def sendMail(recipientOids: Seq[ObjectId], subject: String, body: String, request: Option[HttpServletRequest]): Unit = {
    BWLogger.log(getClass.getName, "sendMail", s"ENTRY", request)

    val emailsEnabled = MailUtils.instanceInfo.has("emails_enabled") && MailUtils.instanceInfo.emails_enabled[Boolean]

    if (emailsEnabled) {
      import scala.concurrent.ExecutionContext.Implicits.global
      Future {
        val allowedRecipients: Seq[DynDoc] = BWMongoDB3.persons.
          find(Map("_id" -> Map("$in" -> recipientOids), "email_enabled" -> true))
        if (allowedRecipients.nonEmpty) {
          val username = "430forest@gmail.com"
          val allowedOids = allowedRecipients.map(_._id[ObjectId])
          val session = MailUtils.session
          val message = new MimeMessage(session)
          message.setFrom(new InternetAddress(username))
          val persons: Seq[DynDoc] = BWMongoDB3.persons.find(Map("_id" -> Map("$in" -> allowedOids)))
          val emails = persons.map(_.emails[Many[Document]].find(_.`type`[String] == "work").head.email[String])
          val recipients: Seq[Address] = emails.flatMap(email => InternetAddress.parse(email))
          message.setRecipients(Message.RecipientType.TO, recipients.toArray)
          message.setSubject(subject)
          val multipart = new MimeMultipart("alternative")
          val textPart = new MimeBodyPart()
          val bodyText = body + (if (body.endsWith("\n")) "" else "\n") + emailSignature
          textPart.setText(bodyText, "utf-8")
          multipart.addBodyPart(textPart)
          val htmlPart = new MimeBodyPart()
          val bodyHtml = s"""<span>${bodyText.replaceAll("\n", "<br/>")}</span>"""
          htmlPart.setContent(bodyHtml, "text/html; charset=utf-8")
          multipart.addBodyPart(htmlPart)
          message.setContent(multipart)
          message.saveChanges()
          Transport.send(message)
          BWLogger.log(getClass.getName, "sendMail", s"EXIT-OK (${recipients.length} recipients)", request)
        } else {
          BWLogger.log(getClass.getName, "sendMail", s"EXIT-OK (NO RECIPIENTS)", request)
        }
      } onComplete {
        case Failure(t: Throwable) =>
          BWLogger.log(getClass.getName, "sendMail", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
          t.printStackTrace()
        case _ =>
      }
    } else {
      BWLogger.log(getClass.getName, "sendMail", s"EXIT-OK (INSTANCE-DISABLED)", request)
    }
  }

}

object MailUtils {

  private val props = new Properties()
  props.put("mail.smtp.host", "smtp.gmail.com")
  props.put("mail.smtp.socketFactory.port", "465")
  props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
  props.put("mail.smtp.auth", "true")
  props.put("mail.smtp.port", "465")
  props.put("mail.smtp.socketFactory.fallback", "false")

  val username = "430forest@gmail.com"
  //val password = "rapid-Fire"

  val instanceInfo: DynDoc = BWMongoDB3.instance_info.find().head

  lazy val session: Session = Session.getInstance(props,
    new javax.mail.Authenticator() {
      override def getPasswordAuthentication: PasswordAuthentication = {
        val emailPass = MailUtils.instanceInfo.email_pass[String]

        new PasswordAuthentication(username,
          s"sdg${emailPass.length - 7}${username.substring(3, emailPass.length - 1)}")
      }
    }
  )

}
