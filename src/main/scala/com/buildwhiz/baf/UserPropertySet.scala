package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import com.buildwhiz.HttpUtils
import org.bson.types.ObjectId

class UserPropertySet extends HttpServlet with HttpUtils {

  private def setProperty(personOid: ObjectId, property: String, value: String): Unit = {
    val enabledRe = "(enabled)".r
    val emailEnabledRe = "(email_enabled)".r
    val roleRe = "role:(.+)".r
    val setterSpec = property match {
      case emailEnabledRe(enabled) => Map("$set" -> Map(enabled -> value.toBoolean))
      case enabledRe(enabled) => Map("$set" -> Map(enabled -> value.toBoolean))
      case roleRe(roleName) => Map((if (value.toBoolean) "$addToSet" else "$pull") -> Map("roles" -> roleName))
    }
    val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> personOid), setterSpec)
    if (updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val property = parameters("property")
      val value = parameters("value")
      setProperty(personOid, property, value)
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
