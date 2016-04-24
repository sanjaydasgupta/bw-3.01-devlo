package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.{CryptoUtils, HttpUtils}
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import BWMongoDB3._
import org.bson.types.ObjectId

class UserPasswordSet extends HttpServlet with HttpUtils with CryptoUtils {

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
