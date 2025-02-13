package com.buildwhiz.web

import java.util.{Calendar, TimeZone}

import com.buildwhiz.baf2.PersonApi
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.types.ObjectId

import scala.collection.JavaConverters._
import scala.language.implicitConversions

class BrowseMails extends HttpServlet with HttpUtils with DateTimeUtils {

  private val calendar = Calendar.getInstance

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val writer = response.getWriter
    writer.println(s"<html><head><title>Mails Listing</title></head><body>")
    try {
      val personOid = new ObjectId(parameters("person_id"))
      val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
      val name = PersonApi.fullName(person)
      writer.println(s"""<h1 align="center">Mails for $name</h1>""")
      val mails: Seq[DynDoc] = BWMongoDB3.mails.find(Map("recipient_person_id" -> personOid))
      if (mails.nonEmpty) {
        writer.println(
          s"""<table border="1" align="center"></tr><td align="center">Time-Stamp</td><td align="center">Subject</td>
              |<td align="center">Message</td></tr>""".stripMargin)
        for (mail <- mails.sortWith((a, b) => a.timestamp[Long] > b.timestamp[Long])) {
          val timeStamp = dateTimeString(mail.timestamp[Long], Some(person.tz[String]))
          //val timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(mail.timestamp[Long]))
          writer.println(
            s"""</tr><td align="center">$timeStamp</td><td align="center">${mail.subject[String]}</td>
                |<td align="center">${mail.message[String]}</td></tr>""".stripMargin)
        }
        writer.println(s"</table>")
      } else {
        writer.println(s"""<h2 align="center">No mails at this time</h2>""")
      }
      writer.println(s"</body></html>")
      response.setContentType("text/html")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

}