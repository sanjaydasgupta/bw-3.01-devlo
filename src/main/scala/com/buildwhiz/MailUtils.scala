package com.buildwhiz

import java.util.Properties
import javax.mail.internet.{InternetAddress, MimeMessage}
import javax.mail._

import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import BWMongoDB3._
import org.bson.types.ObjectId

import scala.concurrent.Future
import scala.util.Failure

import scala.collection.JavaConverters._

trait MailUtils {

  val emailSignature = "\nThe 430 Forest project"

  def sendMail(recipientOid: ObjectId, subject: String, body: String): Unit =
    sendMail(Seq(recipientOid), subject, body)

  def sendMail(recipientOids: Seq[ObjectId], subject: String, body: String): Unit = {
    BWLogger.log(getClass.getName, "sendMail", s"ENTRY")
    val username = "430forest@gmail.com"
    val password = "rapid-Fire"

    val props = new Properties()

    props.put("mail.smtp.host", "smtp.gmail.com")
    props.put("mail.smtp.socketFactory.port", "465")
    props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
    props.put("mail.smtp.auth", "true")
    props.put("mail.smtp.port", "465")
    props.put("mail.smtp.socketFactory.fallback", "false")

    import scala.concurrent.ExecutionContext.Implicits.global
    Future {
      val session = Session.getInstance(props,
        new javax.mail.Authenticator() {
          override def getPasswordAuthentication: PasswordAuthentication = {
            new PasswordAuthentication(username,
              s"sdg${password.length - 7}${username.substring(3, password.length - 1)}")
          }
        }
      )

      val message = new MimeMessage(session)
      message.setFrom(new InternetAddress(username))
      val persons: Seq[DynDoc] = BWMongoDB3.persons.find(Map("_id" -> Map("$in" -> recipientOids))).asScala.toSeq
      val emails = persons.map(_.emails[DocumentList].find(_.`type`[String] == "work").head.email[String])
      //val names = persons.map(person => (person.first_name[String], person.last_name[String]))
      //val emailParts = username.split('@')
      val recipients: Seq[Address] = emails.flatMap(email => InternetAddress.parse(email))
      message.setRecipients(Message.RecipientType.TO, recipients.toArray)
      message.setSubject(subject)
      message.setText(body + (if (body.endsWith("\n")) "" else "\n") + emailSignature)
      Transport.send(message)
      /*import scala.concurrent.ExecutionContext.Implicits.global
      Future(Transport.send(message)).onComplete {
        case Failure(t: Throwable) =>
          BWLogger.log(getClass.getName, "sendMail", s"ERROR: ${t.getClass.getName}(${t.getMessage})")
          t.printStackTrace()
        case _ =>
      }*/
      BWLogger.log(getClass.getName, "sendMail", s"EXIT-OK")
    } /*catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "sendMail", s"ERROR: ${t.getClass.getName}(${t.getMessage})")
        t.printStackTrace()
        throw t
    }*/ .onComplete {
      case Failure(t: Throwable) =>
        BWLogger.log(getClass.getName, "sendMail", s"ERROR: ${t.getClass.getName}(${t.getMessage})")
        t.printStackTrace()
      case _ =>
    }
  }

}
