package com.buildwhiz.baf

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class UserPropertyFetch extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
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

      val emailEnabled: Boolean = if (freshUserRecord.has("email_enabled"))
        freshUserRecord.email_enabled[Boolean]
      else
        false
      val resultFields: Document = Map("email_enabled" -> emailEnabled,
          "phone_work" -> phoneWork, "phone_mobile" -> phoneMobile,
          "email_work" -> emailWork, "email_other" -> emailOther)
      response.getWriter.print(resultFields.toJson)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
