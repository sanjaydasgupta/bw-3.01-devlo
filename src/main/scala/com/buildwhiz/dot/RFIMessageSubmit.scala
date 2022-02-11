package com.buildwhiz.dot

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils, MailUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class RFIMessageSubmit extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  private def messageBody(subject: String, uri: String) =
    s"""${dateTimeString(System.currentTimeMillis)}
      |An RFI message has been posted with the following subject:
      |
      |&nbsp;&nbsp;&nbsp;&nbsp;<a href="$uri">$subject</a>&nbsp;&nbsp;(Click link to see details)
      |
      |This email was sent as you are either a manager or an author.""".stripMargin

  private def sendRFIMail(members: Seq[ObjectId], subject: String, uri: String, request: HttpServletRequest): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"sendMail($members) ENTRY", request)
    try {
      sendMail(members, s"RFI for '$subject'", messageBody(subject, uri), Some(request))
    } catch {
      case t: Throwable =>
        //t.printStackTrace()
        BWLogger.log(getClass.getName, request.getMethod,
            s"sendMail() ERROR ${t.getClass.getName}(${t.getMessage})", request)
        throw t
    }
    BWLogger.log(getClass.getName, request.getMethod, "sendMail() EXIT-OK", request)
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val postData: DynDoc = Document.parse(getStreamData(request))
      val timestamp = System.currentTimeMillis
      val user: DynDoc = getUser(request)
      val senderOid = user._id[ObjectId]
      val text = postData.text[String]
      val recipientRoles: Seq[String] = if (postData.has("recipient_roles"))
        postData.recipient_roles[Many[String]]
      else
        Seq.empty[String]
      //
      // ToDo: handle recipientRoles
      //
      val message = if (postData.has("attachments")) {
        val attachments: Seq[Document] = postData.attachments[String].split("#").
          map(a => {val d = Document.parse(a); if (d.containsKey("$$hashKey")) d.remove("$$hashKey"); d}).toSeq
        new Document(Map("text" -> text, "timestamp" -> timestamp, "sender" -> senderOid,
          "attachments" -> attachments, "read_person_ids" -> Nil))
      } else {
        new Document(Map("text" -> text, "timestamp" -> timestamp, "sender" -> senderOid,
          "read_person_ids" -> Nil))
      }
      val rfiOid = new ObjectId(postData.rfi_id[String])
      BWMongoDB3.rfi_messages.updateOne(Map("_id" -> rfiOid), Map("$push" -> Map("messages" -> message),
        "$set" -> Map("status" -> "active")))
      val rfiMessage: DynDoc = BWMongoDB3.rfi_messages.find(Map("_id" -> rfiOid)).head
      val members: Seq[ObjectId] = if (rfiMessage.has("members"))
        rfiMessage.members[Many[ObjectId]]
      else
        Seq.empty[ObjectId]
      val subject: String = rfiMessage.subject[String]
      val url = request.getRequestURL.toString.split("/").reverse.drop(2).reverse.mkString("/") +
        s"/#/rfi?rfi_id=$rfiOid"
      sendRFIMail(members.filterNot(_ == senderOid), subject, url, request)
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
