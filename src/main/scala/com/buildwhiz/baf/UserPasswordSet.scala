package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.{CryptoUtils, HttpUtils, MailUtils}
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import BWMongoDB3._
import org.bson.types.ObjectId

class UserPasswordSet extends HttpServlet with HttpUtils with CryptoUtils with MailUtils {

  private val mailBody =
    """Your password on '430forest.com' has just been changed.
      |If you have not changed it, please notify your contact person.
      |Please do not reply to this email, this mailbox is not monitored.
    """.stripMargin

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val oldPassword = parameters("old_password")
      val newPassword = parameters("new_password")
       val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> personOid, "password" -> md5(oldPassword)),
        Map("$set" -> Map(s"password" -> md5(newPassword))))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      sendMail(personOid, "Password changed on 430forest.com", mailBody)
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
    BWLogger.log(getClass.getName, "doPost", "EXIT-OK", request)
  }

}
