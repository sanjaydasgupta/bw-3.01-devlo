package com.buildwhiz.baf3

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.buildwhiz.utils.MailUtils3

class HelpMessageSend extends HttpServlet with HttpUtils with MailUtils3 {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val parameterString = getStreamData(request)
      BWLogger.log(getClass.getName, request.getMethod, s"Parameter-String: $parameterString", request)
      val postData = Document.parse(parameterString)
      val subject = postData.getString("subject")
      val body = postData.getString("body")
      val user: DynDoc = getUser(request)
      val fullName = PersonApi.fullName(user)

      val emailSubject = s"Help-Message from $fullName (${user._id[ObjectId]})"
      val emailBody = s"User's Subject: $subject\n\nUser's Message: $body"
      val adminOids = PersonApi.listAdmins.map(_._id[ObjectId])
      sendMail(adminOids, emailSubject, emailBody, Some(request))

      response.getWriter.print(successJson())
      response.setContentType("application/json")
      val message = s"$fullName sent help-message with subject '$subject'"
      BWLogger.log(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}