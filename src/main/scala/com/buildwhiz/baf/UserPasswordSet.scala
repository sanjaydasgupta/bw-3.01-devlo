package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, CryptoUtils, HttpUtils, MailUtils}
import org.bson.Document
import org.bson.types.ObjectId

class UserPasswordSet extends HttpServlet with HttpUtils with CryptoUtils with MailUtils {

  private val mailBody =
    """Your password on '430forest.com' has just been changed.
      |If you have not changed it, please notify your contact person.
    """.stripMargin

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      val postData: DynDoc = Document.parse(getStreamData(request))
      val personOid = new ObjectId(postData.person_id[String])
      if (userOid != personOid)
        throw new IllegalArgumentException("Password change by 3rd party")
      val oldPassword = postData.old_password[String]
      val newPassword = postData.new_password[String]
       val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> personOid, "password" -> md5(oldPassword)),
        Map("$set" -> Map(s"password" -> md5(newPassword))))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      sendMail(personOid, "Password changed on 430forest.com", mailBody, Some(request))
      response.setStatus(HttpServletResponse.SC_OK)
      val thePerson: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
      val personLog = s"'${thePerson.first_name[String]} ${thePerson.last_name[String]}' (${thePerson._id[ObjectId]})"
      BWLogger.audit(getClass.getName, "doPost", s"""Set password for $personLog""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}
