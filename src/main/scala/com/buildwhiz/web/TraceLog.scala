package com.buildwhiz.web

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
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
        val (count, unit) = parameters.get("count") match {
          case None => (50, "rows")
          case Some(theCount) =>
            val withUnitPattern = "([0-9]+)(hours|days|rows)".r
            val withoutUnitPattern = "([0-9]+)".r
            theCount match {
              case withUnitPattern(d, u) => (d.toInt, u)
              case withoutUnitPattern(d) => (d.toInt, "rows")
              case _ => (50, "rows")
            }
        }
        val clientIp = request.getHeader("X-FORWARDED-FOR") match {
          case null => request.getRemoteAddr
          case ip => ip
        }
        val logType = parameters.getOrElse("type", "full")
        val (typeQuery, logTypeName) = logType.toLowerCase match {
          case "error" => (Map("event_name" -> Map("$regex" -> ".*ERROR.+")), "Error")
          case "audit" => (Map("event_name" -> Map("$regex" -> ".*AUDIT.+")), "Audit")
          case "check" => (Map("event_name" -> Map("$regex" -> ".*(AUDIT|ERROR).+")), "Check")
          case _ => (Map.empty[String, AnyRef], "Full")
        }
        writer.println(s"""<body><h2 align="center">$logTypeName Log ($count $unit)</h2>""")
        writer.println("<table border=\"1\" style=\"width: 100%;\">")
        val widths = Seq(10, 10, 10, 35, 35)
        writer.println(List("Timestamp", "Process", "Activity", "Event", "Variables").zip(widths).
          map(p => s"""<td style="width: ${p._2}%;" align="center">${p._1}</td>""").
          mkString("<tr bgcolor=\"cyan\">", "", "</tr>"))
        val labels = List("milliseconds", "process_id", "activity_name", "event_name", "variables")
        val traceLogCollection = BWMongoDB3.trace_log
        val traceLogDocs: Seq[DynDoc] = unit match {
          case "hours" =>
            val ms = System.currentTimeMillis
            val timeSince = ms - 3600L * 1000L * count.asInstanceOf[Long]
            traceLogCollection.find(typeQuery ++
              Map("milliseconds" -> Map("$gte" -> timeSince))).sort(Map("milliseconds" -> -1))
          case "days" =>
            val ms = System.currentTimeMillis
            val timeSince = ms - 86400L * 1000L * count.asInstanceOf[Long]
            traceLogCollection.find(typeQuery ++
              Map("milliseconds" -> Map("$gte" -> timeSince))).sort(Map("milliseconds" -> -1))
          case _ =>
            traceLogCollection.find(typeQuery).sort(Map("milliseconds" -> -1)).limit(count)
        }

        for (doc <- traceLogDocs) {
          val fields = labels.map(doc.asDoc.get).toBuffer
          fields(0) = dateTimeString(fields.head.asInstanceOf[Long], parameters.get("tz").orElse(Some("Asia/Calcutta")))
          fields(fields.length - 1) = prettyPrint(fields.last.asInstanceOf[Document])
          val htmlRowData = fields.zip(widths).
            map(p => {
              val text = addSpaces(p._1.toString)
              val color = if (text.toLowerCase.contains("error")) "red" else "black"
              s"""<td style="width: ${p._2}%; color:$color" align="center">$text</td>"""
            }).mkString.replaceAll("\n+", " ")
          val user: DynDoc = getUser(request)
          val fullName = s"${user.first_name[String]} ${user.last_name[String]}"
          if (htmlRowData.contains(clientIp) || htmlRowData.contains(s"u$$nm: $fullName"))
            writer.println(s"""<tr style="background-color: beige;">$htmlRowData</tr>""")
          else
            writer.println(s"<tr>$htmlRowData</tr>")
        }
        if (traceLogDocs.isEmpty)
          writer.println(s"""<tr><td colspan="5" align="center">No such rows!</td></tr>""")
        writer.println("</table>")

        val hourCountLinks = Seq(1, 2, 6, 18).
          map(n => if (unit == "hours" && count == n) s"($n)" else n).
          map(n => s"""<a href="$urlName?count=${n}hours&type=$logType">$n</a>""").mkString("&nbsp;&nbsp;")
        val dayCountLinks = Seq(1, 2, 7, 14, 28).
          map(n => if (unit == "days" && count == n) s"($n)" else n).
          map(n => s"""<a href="$urlName?count=${n}days&type=$logType">$n</a>""").mkString("&nbsp;&nbsp;")
        val rowCountLinks = Seq(50, 100, 200, 500).
          map(n => if (unit == "rows" && count == n) s"($n)" else n).
          map(n => s"""<a href="$urlName?count=${n}rows&type=$logType">$n</a>""").mkString("&nbsp;&nbsp;")
        val typeLinks = Seq("all", "audit", "error", "check").
          map(t => if (logType == t) s"($t)" else t).
          map(t => s"""<a href="$urlName?count=$count$unit&type=$t">$t</a>""").mkString("&nbsp;&nbsp;")

        writer.println(s"""<h3 align=\"center\">hours: $hourCountLinks, &nbsp;&nbsp;&nbsp;&nbsp;days: $dayCountLinks,
        &nbsp;&nbsp;&nbsp;&nbsp;rows: $rowCountLinks, &nbsp;&nbsp;&nbsp;&nbsp;days: $typeLinks, </h3>""")

        writer.println("</body></html>")
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