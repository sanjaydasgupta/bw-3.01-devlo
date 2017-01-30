package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import BWMongoDB3._
import com.buildwhiz.utils.{CryptoUtils, HttpUtils, MailUtils}
import org.bson.Document
import org.bson.types.ObjectId

class UserPasswordSet extends HttpServlet with HttpUtils with CryptoUtils with MailUtils {

  private val mailBody =
    """Your password on '430forest.com' has just been changed.
      |If you have not changed it, please notify your contact person.
    """.stripMargin

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val postData: DynDoc = Document.parse(getStreamData(request))
      val personOid = new ObjectId(postData.person_id[String])
      val oldPassword = postData.old_password[String]
      val newPassword = postData.new_password[String]
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
