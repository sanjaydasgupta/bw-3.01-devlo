package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class UserPropertyFetch extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head

      val phones: Seq[DynDoc] = if (freshUserRecord.has("phones"))
        freshUserRecord.phones[Many[Document]]
      else
        Seq.empty[DynDoc]
      val phoneWork = phones.find(_.`type`[String] == "work") match {
        case Some(ph) => ph.phone[String]
        case None => ""
      }
      val phoneMobile = phones.find(_.`type`[String] == "mobile") match {
        case Some(ph) => ph.phone[String]
        case None => ""
      }

      val emails: Seq[DynDoc] = if (freshUserRecord.has("emails"))
        freshUserRecord.emails[Many[Document]]
      else
        Seq.empty[DynDoc]
      val emailOther = emails.find(_.`type`[String] == "other") match {
        case Some(em) => em.email[String]
        case None => ""
      }
      val emailWork = emails.find(_.`type`[String] == "work") match {
        case Some(em) => em.email[String]
        case None => ""
      }

      val textEnabled: Boolean = if (freshUserRecord.has("text_enabled"))
        freshUserRecord.text_enabled[Boolean]
      else
        false

      val emailEnabled: Boolean = if (freshUserRecord.has("email_enabled"))
        freshUserRecord.email_enabled[Boolean]
      else
        false

      val defaultTimezone: String = if (freshUserRecord.has("default_timezone"))
        freshUserRecord.default_timezone[String]
      else
        "project"

      val phoneCanText = if (freshUserRecord.has("phone_can_text"))
        freshUserRecord.phone_can_text[Boolean]
      else
        false

      val fontSize: String = if (freshUserRecord.has("font_size"))
        freshUserRecord.font_size[String]
      else
        "normal"

      val resultFields: Document = Map("email_enabled" -> emailEnabled,
          "text_enabled" -> textEnabled, "default_timezone" -> defaultTimezone,
          "phone_work" -> phoneWork, "phone_mobile" -> phoneMobile, "phone_can_text" -> phoneCanText,
          "email_work" -> emailWork, "email_other" -> emailOther, "font_size" -> fontSize)

      response.getWriter.print(resultFields.toJson)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
