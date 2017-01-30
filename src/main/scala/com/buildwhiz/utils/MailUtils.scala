package com.buildwhiz.utils

import java.util.Properties
import javax.mail._
import javax.mail.internet.{InternetAddress, MimeBodyPart, MimeMessage, MimeMultipart}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.types.ObjectId

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.Failure

trait MailUtils {

  val emailSignature: String =
    """
      |Please do not reply to this email, this mailbox is not monitored.
      |
      |The 430 Forest project""".stripMargin

  def sendMail(recipientOid: ObjectId, subject: String, body: String): Unit =
    sendMail(Seq(recipientOid), subject, body)

  def sendMail(recipientOids: Seq[ObjectId], subject: String, body: String): Unit = {
    BWLogger.log(getClass.getName, "sendMail", s"ENTRY")
    val username = "430forest@gmail.com"

    import scala.concurrent.ExecutionContext.Implicits.global
    Future {
      val session = MailUtils.session

      val allowedRecs: Seq[DynDoc] = BWMongoDB3.persons.
        find(Map("_id" -> Map("$in" -> recipientOids), "email_enabled" -> true)).asScala.toSeq
      val allowedOids = allowedRecs.map(_._id[ObjectId])

      val message = new MimeMessage(session)
      message.setFrom(new InternetAddress(username))
      val persons: Seq[DynDoc] = BWMongoDB3.persons.find(Map("_id" -> Map("$in" -> allowedOids))).asScala.toSeq
      val emails = persons.map(_.emails[DocumentList].find(_.`type`[String] == "work").head.email[String])
      //val names = persons.map(person => (person.first_name[String], person.last_name[String]))
      //val emailParts = username.split('@')
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
      BWLogger.log(getClass.getName, "sendMail", s"EXIT-OK")
    } onComplete {
      case Failure(t: Throwable) =>
        BWLogger.log(getClass.getName, "sendMail", s"ERROR: ${t.getClass.getName}(${t.getMessage})")
        t.printStackTrace()
      case _ =>
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
  val password = "rapid-Fire"

  lazy val session: Session = Session.getInstance(props,
    new javax.mail.Authenticator() {
      override def getPasswordAuthentication: PasswordAuthentication = {
        new PasswordAuthentication(username,
          s"sdg${password.length - 7}${username.substring(3, password.length - 1)}")
      }
    }
  )

}
