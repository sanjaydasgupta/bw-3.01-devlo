package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, CryptoUtils, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class UserPasswordSet extends HttpServlet with HttpUtils with CryptoUtils with MailUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      val postData: DynDoc = Document.parse(getStreamData(request))
      val oldPassword = postData.old_password[String]
      val newPassword = postData.new_password[String]
       val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> userOid, "password" -> md5(oldPassword)),
        Map("$set" -> Map(s"password" -> md5(newPassword))))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      val info: DynDoc = BWMongoDB3.instance_info.find().head
      val instanceName = info.instance[String]
      val mailBody =
        s"""Your password on '$instanceName' has just been changed.
          |If you have not changed it, please notify your contact person.
        """.stripMargin
      sendMail(Seq(userOid), s"Password changed on '$instanceName'", mailBody, Some(request))
      response.setStatus(HttpServletResponse.SC_OK)
      val thePerson: DynDoc = BWMongoDB3.persons.find(Map("_id" -> userOid)).head
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
