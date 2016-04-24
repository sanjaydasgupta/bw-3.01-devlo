package com.buildwhiz

import java.util.Properties
import javax.mail.internet.{InternetAddress, MimeMessage}
import javax.mail._

import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import BWMongoDB3._
import org.bson.types.ObjectId

import scala.concurrent.Future
import scala.util.Failure

import scala.collection.JavaConversions._

trait MailUtils {

  def sendMail(recipientOid: ObjectId, subject: String, body: String): Unit = {
    BWLogger.log(getClass.getName, "sendMail", s"ENTRY")
    val username = "sanjay.dasgupta@buildwhiz.com"
    val password = "rapid-Fire"

    val props = new Properties()

    props.put("mail.smtp.host", "smtp.gmail.com")
    props.put("mail.smtp.socketFactory.port", "465")
    props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
    props.put("mail.smtp.auth", "true")
    props.put("mail.smtp.port", "465")
    props.put("mail.smtp.socketFactory.fallback", "false")

    try {
      val session = Session.getInstance(props,
        new javax.mail.Authenticator() {
          override def getPasswordAuthentication = {
            new PasswordAuthentication(username,
              s"${username.substring(16, 21)}${password.length/2}${username.substring(21, 25)}")
          }
        }
      )

      val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> recipientOid)).head
      val email = person.emails[DocumentList].find(_.`type`[String] == "work").head.email[String]
      val (firstName, lastName) = (person.first_name[String], person.last_name[String])
      val message = new MimeMessage(session)
      message.setFrom(new InternetAddress(username))
      val emailParts = username.split('@')
      val recipients: Array[Address] = InternetAddress.parse(s"sanjay.dasgupta+$firstName.$lastName@buildwhiz.com").
        map(_.asInstanceOf[Address])
      message.setRecipients(Message.RecipientType.TO, recipients)
      message.setSubject(s"$subject for $email")
      message.setText(body)
      import scala.concurrent.ExecutionContext.Implicits.global
      Future(Transport.send(message)).onComplete {
        case Failure(t: Throwable) =>
          BWLogger.log(getClass.getName, "sendMail", s"ERROR: ${t.getClass.getName}(${t.getMessage})")
          t.printStackTrace()
        case _ =>
      }
      BWLogger.log(getClass.getName, "sendMail", s"EXIT-OK")
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "sendMail", s"ERROR: ${t.getClass.getName}(${t.getMessage})")
        t.printStackTrace()
        throw t
    }
  }

}
