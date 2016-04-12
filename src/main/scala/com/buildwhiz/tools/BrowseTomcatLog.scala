package com.buildwhiz.tools

import java.io.{File, PrintWriter}
import java.text.SimpleDateFormat
import java.util.Calendar
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import scala.collection.mutable
import scala.io.Source

class BrowseTomcatLog extends HttpServlet {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val pw = response.getWriter
    pw.println("<html><head><title>Tomcat Logs</title></head><body>")
    pw.println("<h2 align=\"center\">Tomcat Logs</h2>")
    val tomcatDir = new File("server").listFiles.filter(_.getName.startsWith("apache-tomcat-")).head
    val logDirectory = new File(tomcatDir, "logs")
    if (logDirectory.exists) {
      logDirectory.listFiles.filter(_.getName.matches("catalina\\.\\d{4,4}-\\d{2,2}-\\d{2,2}\\.log")).toSeq match {
        case Nil =>
          pw.println("<span style=\"background-color: red;\">&nbsp;No log-files found&nbsp;</span><br/>")
        case logFiles =>
          val sortedFiles = logFiles.map(lf => (lf.getName, lf)).sortBy(_._1).map(_._2).reverse
          pw.println("<table border=\"1\" align=\"center\">")
          pw.println(
            s"""<tr><td align="center">File</td><td align="center">Length</td><td align="center">Status</td>
               |<td align="center">Delete</td></tr>""".stripMargin)
          for (file <- sortedFiles) {
            pw.println(
              s"""<tr><td align="center">${file.getName}</td><td align="center">${file.length()}</td>
                 |<td align="center">-</td><td align="center">-</td></tr>""".stripMargin)
          }
      }
    } else {
      pw.println(s"""<span style="background-color: red">No 'logs' directory in '${tomcatDir.getAbsolutePath}</span>""")
    }
    pw.println("</body></html>")
    response.setStatus(HttpServletResponse.SC_OK)
  }
  
  private def processFile(logFile: File, pw: PrintWriter): Unit = {
    def highlight(msg: String): String = {
      if (msg.startsWith("INFO: "))
        s"<td>$msg</td>"
      else
        s"""<td style="background-color: yellow;">$msg</td>"""
    }
    def long2hhmmss(millis: Long): String = {
      val cal  = Calendar.getInstance
      cal.setTimeInMillis(millis)
      "%02d:%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND))
    }
    val reportTime = System.currentTimeMillis
    var startTime: Option[Long] = None
    var lastErrorTime: Option[Long] = None
    val logSource = Source.fromFile(logFile)
    var currentTime = 0L
    var isInteresting = false
    var message: Option[String] = None
    val messageData = mutable.ListBuffer.empty[(Long, String)]
    var infoLine = ""
    for (line <- logSource.getLines) {
      if (line.matches("[A-Z]+\\:.+")) {
        if (line.startsWith("SEVERE: ")) {
          lastErrorTime = Some(currentTime)
          infoLine = line
          isInteresting = true
        } else if (line.startsWith("INFO: Deploying web application archive")) {
          infoLine = line
          isInteresting = true
        }
      } else if (line.matches("[JFMASOND][a-z]{2,2} \\d{1,2}, \\d{4,4} \\d{1,2}:\\d{2,2}:\\d{2,2} [AP]M .+")) {
        if (isInteresting) {
          if (message.isDefined) {
            messageData.append((currentTime, message.get))
          } else if (infoLine.split("\\s+").length > 10 || infoLine.startsWith("INFO: Deploying web application archive")) {
            messageData.append((currentTime, infoLine))
          }
          message = None
          isInteresting = false
        }
        currentTime = simpleDateFormat.parse(line).getTime
        if (startTime.isEmpty)
          startTime = Some(currentTime)
      } else if (isInteresting && message.isEmpty && 
          (line.contains(" line ") || line.contains(" column ") || line.contains("buildwhiz"))) {
        message = Some(line)
      } 
    }
    val endTime = currentTime
    if (isInteresting) {
      if (message.isDefined) {
        messageData.append((currentTime, message.get))
      } else if (infoLine.split("\\s+").length > 10 || infoLine.startsWith("INFO: Deploying web application archive")) {
        messageData.append((currentTime, infoLine))
      }
    }
    logSource.close()
    if (startTime.isDefined) {
      pw.println(s"Log start-time: ${long2hhmmss(startTime.get)}<br/>")
      if (messageData.isEmpty) {
        pw.println("<span style=\"background-color: #00ff00\">&nbsp;No significant events found&nbsp;<br/></span>")
      } else {
        pw.println(messageData.map(p => s"<td>${long2hhmmss(p._1)}</td>${highlight(p._2)}").
            mkString("<table border=\"1\"><tr>", 
                "</tr><tr>", "</tr></table>"))
      }
      if (endTime != 0)
        pw.println(s"Log end-time: ${long2hhmmss(endTime)}<br/>")
        pw.println(s"Report time: ${long2hhmmss(reportTime)}<br/>")
    } else {
      pw.println("<span style=\"background-color: red\">&nbsp;No log data found&nbsp;</span>")
    }
  }

  private val simpleDateFormat = new SimpleDateFormat("MMM dd, YYYY K:mm:ss a")
}
