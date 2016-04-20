package com.buildwhiz.web

import java.util.{Calendar, TimeZone}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import org.bson.Document

import scala.collection.JavaConversions._
import scala.collection.mutable

class TraceLog extends HttpServlet {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val writer = response.getWriter
    try {
      val urlName = request.getRequestURL.toString.split("/").last
      val parameters: mutable.Map[String, String] = request.getParameterMap.map(p => (p._1, p._2.mkString))
      val count = parameters.get("count") match {
        case None => 50
        case Some(c) => c.toInt
      }
      writer.println("<html><head><title>BuildWhiz Trace Information</title></head>")
      val clientIp = request.getHeader("X-FORWARDED-FOR") match {
        case null => request.getRemoteAddr
        case ip => ip
      }
      writer.println(s"""<body><h2 align="center">Log Trace Information ($count lines) for $clientIp</h2>""")
      writer.println("<table border=\"1\" style=\"width: 100%;\">")
      val widths = Seq(10, 10, 10, 35, 35)
      writer.println(List("Timestamp", "Process", "Activity", "Event", "Variables").zip(widths).
        map(p => s"""<td style="width: ${p._2}%;" align="center">${p._1}</td>""").
        mkString("<tr bgcolor=\"cyan\">", "", "</tr>"))
      val labels = List("milliseconds", "process_id", "activity_name", "event_name", "variables")
      val traceLogCollection = BWMongoDB3.trace_log
      val traceLogDocs: Seq[Document] = traceLogCollection.find().sort(Map("milliseconds" -> -1)).limit(count).toSeq
      for (doc <- traceLogDocs) {
        val fields = labels.map(doc.get).toBuffer
        fields(0) = prettyPrint(fields.head.asInstanceOf[Long])
        fields(fields.length - 1) = prettyPrint(fields.last.asInstanceOf[Document])
        val htmlRowData = fields.zip(widths).map(p => s"""<td style="width: ${p._2}%;" align="center">${p._1}</td>""").mkString
        if (htmlRowData.contains(clientIp))
          writer.println(s"""<tr style="background-color: yellow;">$htmlRowData</tr>""")
        else
          writer.println(s"<tr>$htmlRowData</tr>")
      }
      writer.println("</table></body></html>")
      val links = Seq(100, 500, 2000).map(n => s"""<a href="$urlName?count=$n">$n</a>""").mkString("&nbsp;" * 5)
      writer.println(s"""<h3 align=\"center\">$links</h3>""")
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable => writer.println(s"""<span style="background-color: red;">${t.getClass.getSimpleName}(${t.getMessage})</span>""")
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
    }
  }

  private def prettyPrint(d: Document): String = {
    val mm: mutable.Map[String, Object] = d
    mm.map(p => s"${p._1}: ${p._2}").mkString(", ")
  }

  private def prettyPrint(ms: Long): String = {
    calendar.setTimeInMillis(ms)
    calendar.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"))
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val date = calendar.get(Calendar.DAY_OF_MONTH)
    val hours = calendar.get(Calendar.HOUR_OF_DAY)
    val minutes = calendar.get(Calendar.MINUTE)
    val seconds = calendar.get(Calendar.SECOND)
    "%02d:%02d:%02d %d-%02d-%02d".format(hours, minutes, seconds, year, month, date)
  }

  private val calendar = Calendar.getInstance

}