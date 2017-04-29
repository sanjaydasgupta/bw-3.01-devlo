package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId
import org.bson.Document

class UserPropertySet extends HttpServlet with HttpUtils {

  private def setProperty(personOid: ObjectId, property: String, value: String): Unit = {
    val enabledRe = "(enabled)".r
    val emailEnabledRe = "(email_enabled)".r
    //val firstNameRe = "(first_name)".r
    //val lastNameRe = "(last_name)".r
    val emailTypeRe = "email_(work|other)".r
    val phoneTypeRe = "phone_(work|mobile)".r
    //val roleRe = "role:(.+)".r
    val role2Re = "(view|edit):(.+)".r
    val setterSpec = property match {
      case emailEnabledRe(enabled) => Map("$set" -> Map(enabled -> value.toBoolean))
      case enabledRe(enabled) => Map("$set" -> Map(enabled -> value.toBoolean))
      //case firstNameRe(fn) => Map("$set" -> Map(fn -> value))
      //case lastNameRe(ln) => Map("$set" -> Map(ln -> value))
      case emailTypeRe(emailType) =>
        val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
        val emails: Seq[DynDoc] = person.emails[Many[Document]]
        val idx = emails.indexWhere(_.`type`[String] == emailType)
        if (idx == -1) {
          Map("$push" -> Map("emails" -> Map("type" -> emailType, "email" -> value)))
        } else {
          Map("$set" -> Map(s"emails.$idx.email" -> value))
        }
      case phoneTypeRe(phoneType) =>
        val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
        val phones: Seq[DynDoc] = person.phones[Many[Document]]
        val idx = phones.indexWhere(_.`type`[String] == phoneType)
        if (idx == -1) {
          Map("$push" -> Map("phones" -> Map("type" -> phoneType, "phone" -> value)))
        } else {
          Map("$set" -> Map(s"phones.$idx.phone" -> value))
        }
      //case roleRe(roleName) => Map((if (value.toBoolean) "$addToSet" else "$pull") -> Map("roles" -> roleName))
      case role2Re(qualifier, roleName) => if (value.toBoolean)
        Map("$addToSet" -> Map("roles" -> s"$qualifier:$roleName")) else
        Map("$pullAll" -> Map("roles" -> Seq(s"$qualifier:$roleName", roleName)))
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
      val user: DynDoc = getUser(request)
      if (user._id[ObjectId] != personOid && !user.roles[Many[String]].contains("BW-Admin"))
        throw new IllegalArgumentException("3rd party property change attempt")
      val properties = parameters("property").split("\\|")
      val values = parameters("value").split("\\|")
      properties.zip(values).foreach(pv => setProperty(personOid, pv._1, pv._2))
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
