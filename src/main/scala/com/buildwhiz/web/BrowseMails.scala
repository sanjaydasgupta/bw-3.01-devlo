package com.buildwhiz.web

import java.util.{Calendar, TimeZone}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.Utils
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import org.bson.types.ObjectId

import scala.collection.JavaConversions._
import scala.language.implicitConversions

class BrowseMails extends HttpServlet with Utils {

  private val calendar = Calendar.getInstance

  private def prettyPrint(ms: Long, tz: Option[String]): String = {
    calendar.setTimeInMillis(ms)
    tz match {
      case Some("pst") =>
        calendar.setTimeZone(TimeZone.getTimeZone("US/Pacific"))
      case _ =>
        calendar.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"))
    }
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val date = calendar.get(Calendar.DAY_OF_MONTH)
    val hours = calendar.get(Calendar.HOUR_OF_DAY)
    val minutes = calendar.get(Calendar.MINUTE)
    val seconds = calendar.get(Calendar.SECOND)
    "%02d:%02d:%02d %d-%02d-%02d".format(hours, minutes, seconds, year, month, date)
  }
  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    val writer = response.getWriter
    writer.println(s"<html><head><title>Mails Listing</title></head><body>")
    try {
      val tz = parameters.get("tz")
      val personOid = new ObjectId(parameters("person_id"))
      val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> personOid)).head
      val name = s"${person.first_name[String]} ${person.last_name[String]}"
      writer.println(s"""<h1 align="center">Mails for $name</h1>""")
      val mails: Seq[DynDoc] = BWMongoDB3.mails.find(Map("recipient_person_id" -> personOid)).toSeq
      if (mails.nonEmpty) {
        writer.println(
          s"""<table border="1" align="center"></tr><td align="center">Time-Stamp</td><td align="center">Subject</td>
              |<td align="center">Message</td></tr>""".stripMargin)
        for (mail <- mails.sortWith((a, b) => a.timestamp[Long] > b.timestamp[Long])) {
          val timeStamp = prettyPrint(mail.timestamp[Long], tz)
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
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

}