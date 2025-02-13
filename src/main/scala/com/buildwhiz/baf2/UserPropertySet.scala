package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.slack.SlackApi
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class UserPropertySet extends HttpServlet with HttpUtils {

  private def setProperty(personOid: ObjectId, property: String, value: String): Unit = {
    val selectedProjectOrPhase = "selected_((?:project|phase)_id)".r
    val enabledRe = "(enabled)".r
    val emailEnabledRe = "(email_enabled)".r
    val textEnabledRe = "(text_enabled)".r
    val booleanRe = "(true|false)".r
    val defaultTimezoneRe = "(default_timezone)".r
    val timezoneRe = "(user|project)".r
    val fontSizePropertyRe = "(font_size)".r
    val fontSizeRe = "(small|normal|large)".r
    val emailTypeRe = "email_(work|other)".r
    val phoneTypeRe = "phone_(work|mobile)".r
    val phoneCanTextRe = "(phone_can_text)".r
    val role2Re = "(view|edit):(.+)".r
    val setterSpec = (property, value) match {
      case (selectedProjectOrPhase(propName), id) =>
        if (propName == "project_id") {
          try {
            val oid = new ObjectId(id)
            Map("$set" -> Map("selected_project_id" -> oid), $unset -> Map("selected_phase_id" -> true))
          } catch {
            case _: Throwable =>
              Map($unset -> Map("selected_project_id" -> true, "selected_phase_id" -> true))
          }
        } else {
          try {
            val oid = new ObjectId(id)
            Map("$set" -> Map("selected_phase_id" -> oid))
          } catch {
            case _: Throwable =>
              Map($unset -> Map("selected_phase_id" -> true))
          }
        }
      case (textEnabledRe(propName), booleanRe(enabled)) => Map("$set" -> Map(propName -> enabled.toBoolean))
      case (emailEnabledRe(propName), booleanRe(enabled)) => Map("$set" -> Map(propName -> enabled.toBoolean))
      case (enabledRe(propName), booleanRe(enabled)) => Map("$set" -> Map(propName -> enabled.toBoolean))
      case (defaultTimezoneRe(propName), timezoneRe(timeZone)) => Map("$set" -> Map(propName -> timeZone))
      case (fontSizePropertyRe(propName), fontSizeRe(size)) => Map("$set" -> Map(propName -> size))
      case (phoneCanTextRe(propName), booleanRe(enabled)) =>
        Map("$set" -> Map(propName -> enabled.toBoolean))
      case (emailTypeRe(emailType), _) =>
        val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
        val emails: Seq[DynDoc] = person.emails[Many[Document]]
        val idx = emails.indexWhere(_.`type`[String] == emailType)
        if (idx == -1) {
          Map("$push" -> Map("emails" -> Map("type" -> emailType, "email" -> value)))
        } else {
          Map("$set" -> Map(s"emails.$idx.email" -> value))
        }
      case (phoneTypeRe(phoneType), _) =>
        val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
        val phones: Seq[DynDoc] = person.phones[Many[Document]]
        val idx = phones.indexWhere(_.`type`[String] == phoneType)
        if (idx == -1) {
          Map("$push" -> Map("phones" -> Map("type" -> phoneType, "phone" -> value)))
        } else {
          Map("$set" -> Map(s"phones.$idx.phone" -> value))
        }
      //case roleRe(roleName) => Map((if (value.toBoolean) "$addToSet" else "$pull") -> Map("roles" -> roleName))
      case (role2Re(qualifier, roleName), _) => if (value.toBoolean)
        Map("$addToSet" -> Map("roles" -> s"$qualifier:$roleName")) else
        Map("$pullAll" -> Map("roles" -> Seq(s"$qualifier:$roleName", roleName)))
    }
    val updateResult = BWMongoDB3.persons.updateOne(Map("_id" -> personOid), setterSpec)
    if (updateResult.getMatchedCount == 0 && updateResult.getModifiedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val userRecord: DynDoc = getUser(request)
      val personOid: ObjectId = parameters.get("person_id") match {
        case None => userRecord._id[ObjectId]
        case Some(pid) => new ObjectId(pid)
      }
      val properties = parameters("property").split("\\|").map(_.trim)
      val values = parameters("value").split("\\|").map(_.trim)
      if (properties.length != values.length)
        throw new IllegalArgumentException("Unmatched properties and values")
      if (userRecord._id[ObjectId] != personOid && !PersonApi.isBuildWhizAdmin(Right(userRecord)))
        throw new IllegalArgumentException("Not permitted")
      properties.zip(values).foreach(pv => setProperty(personOid, pv._1, pv._2))
      val thePerson: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
      val propertiesToNotify = properties.filterNot(_.matches("selected_(?:project|phase)_id"))
      if (propertiesToNotify.nonEmpty) {
        val notificationMessage = s"""Updated ${propertiesToNotify.mkString(", ")} for '${PersonApi.fullName(thePerson)}'"""
        SlackApi.sendNotification(notificationMessage, Left(thePerson), None, Some(request))
      }
      val logMessage = s"""Updated ${properties.mkString(", ")} for '${PersonApi.fullName(thePerson)}'"""
      BWLogger.audit(getClass.getName, request.getMethod, logMessage, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
