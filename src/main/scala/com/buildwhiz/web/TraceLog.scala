package com.buildwhiz.web

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{AmazonS3, BWMongoDB3}
import BWMongoDB3._
import com.buildwhiz.utils.{DateTimeUtils, HttpUtils}
import org.bson.Document
import java.io._
import java.util.TimeZone

import scala.collection.JavaConverters._
import scala.collection.mutable

class TraceLog extends HttpServlet with HttpUtils with DateTimeUtils {

  private def addSpaces(line: String): String = {
    if (line.matches(".*([\\?&][^=]+=[^&\\)]*){2,}.*") || line.split("%20").length > 2 || line.split(",\\S").length > 1)
      line.replaceAll("&", "&amp; ").replaceAll("%20", " ").replaceAll(",(\\S)", ", $1")
    else
      line
  }

  private def exportLogsToS3(): Long = {
    if (TimeZone.getDefault.getRawOffset != 0)
      throw new IllegalArgumentException("Not on AWS")
    val currentDirectory = new File(".")
    val serverDirectory = new File(currentDirectory, "server")
    val tomcatDirectory = serverDirectory.listFiles.filter(_.getName.startsWith("apache-tomcat-")).head
    val logDirectory = new File(tomcatDirectory, "logs")
    val logFile = new File(logDirectory, "catalina.out")
    val res = AmazonS3.putObject("catalina.out", logFile)
    res.getMetadata.getContentLength
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val writer = response.getWriter
    try {
      val parameters: mutable.Map[String, String] = getParameterMap(request)
      writer.println("<html><head><title>BuildWhiz Trace Information</title></head>")
      if (parameters.contains("type") && parameters("type") == "export") {
        val length = exportLogsToS3()
        writer.println(s"""<body><h2 align="center">Exported $length bytes from 'catalina.out' to S3</h2>""")
      } else {
        val urlName = request.getRequestURL.toString.split("/").last
        val count = parameters.get("count") match {
          case None => 50
          case Some(c) => c.toInt
        }
        val clientIp = request.getHeader("X-FORWARDED-FOR") match {
          case null => request.getRemoteAddr
          case ip => ip
        }
        val (query, logType) = parameters.get("type").map(_.toLowerCase) match {
          case Some("error") => (Map("event_name" -> Map("$regex" -> ".*ERROR.+")), "Error")
          case Some("audit") => (Map("event_name" -> Map("$regex" -> ".*AUDIT.+")), "Audit")
          case Some(_) | None => (Map.empty[String, AnyRef], "Full")
        }
        writer.println(s"""<body><h2 align="center">$logType Log</h2>""")
        writer.println("<table border=\"1\" style=\"width: 100%;\">")
        val widths = Seq(10, 10, 10, 35, 35)
        writer.println(List("Timestamp", "Process", "Activity", "Event", "Variables").zip(widths).
          map(p => s"""<td style="width: ${p._2}%;" align="center">${p._1}</td>""").
          mkString("<tr bgcolor=\"cyan\">", "", "</tr>"))
        val labels = List("milliseconds", "process_id", "activity_name", "event_name", "variables")
        val traceLogCollection = BWMongoDB3.trace_log
        val traceLogDocs: Seq[DynDoc] = traceLogCollection.find(query).sort(Map("milliseconds" -> -1)).limit(count)
        for (doc <- traceLogDocs) {
          val fields = labels.map(doc.asDoc.get).toBuffer
          fields(0) = dateTimeString(fields.head.asInstanceOf[Long], parameters.get("tz").orElse(Some("Asia/Calcutta")))
          fields(fields.length - 1) = prettyPrint(fields.last.asInstanceOf[Document])
          val htmlRowData = fields.zip(widths).
            map(p => s"""<td style="width: ${p._2}%;" align="center">${addSpaces(p._1.toString)}</td>""").mkString
          val user: DynDoc = getUser(request)
          val fullName = s"${user.first_name[String]} ${user.last_name[String]}"
          if (htmlRowData.contains(clientIp) || htmlRowData.contains(s"u$$nm: $fullName"))
            writer.println(s"""<tr style="background-color: yellow;">$htmlRowData</tr>""")
          else
            writer.println(s"<tr>$htmlRowData</tr>")
        }
        writer.println("</table></body></html>")
        val links = Seq(100, 500, 2000).map(n => s"""<a href="$urlName?count=$n&type=$logType">$n</a>""").mkString("&nbsp;" * 5)
        writer.println(s"""<h3 align=\"center\">$links</h3>""")
        response.setStatus(HttpServletResponse.SC_OK)
      }
    } catch {
      case t: Throwable => writer.println(s"""<span style="background-color: red;">${t.getClass.getSimpleName}(${t.getMessage})</span>""")
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
    }
  }

  private def prettyPrint(d: Document): String = {
    val mm: mutable.Map[String, Object] = d.asScala
    mm.map(p => s"${p._1}: ${p._2}").mkString(", ")
  }

}