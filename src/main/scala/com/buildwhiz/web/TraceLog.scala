package com.buildwhiz.web

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{DateTimeUtils, HttpUtils}
import org.bson.Document

import java.util.regex.Pattern
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.jdk.CollectionConverters._
import scala.collection.mutable

class TraceLog extends HttpServlet with HttpUtils with DateTimeUtils {

  private val ipPattern = "(?:\\d{1,3}\\.){3}\\d{1,3}|(?:[\\da-fA-F]{1,4}\\:){7}[\\da-fA-F]{1,4}"
  private val reverseEventPattern = Pattern.compile("^\\s*(%s)\\s*[:=]\\s*tneil[cC](.+)$".format(ipPattern),
      Pattern.DOTALL)

  private def addSpaces(line: String): String = {
    if (line.matches(".*([\\?&][^=]+=[^&\\)]*){2,}.*") || line.split("%20").length > 2 || line.split(",\\S").length > 1)
      line.replaceAll("&", "&amp; ").replaceAll("%20", " ").replaceAll(",(\\S)", ", $1")
    else
      line
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val writer = response.getWriter
    try {
      val parameters: mutable.Map[String, String] = getParameterMap(request)
      writer.println("<html><head><title>BuildWhiz Trace Information</title></head>")
      val urlName = request.getRequestURL.toString.split("/").last
      val (duration, durationUnit) = parameters.get("count") match {
        case None => (50, "rows")
        case Some(theCount) =>
          val withUnitPattern = "([\\d.]+)(hours|days|rows)".r
          val withoutUnitPattern = "([\\d.]+)".r
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
      val logType = parameters.getOrElse("type", "any")
      val user: DynDoc = getUser(request)
      val (typeQuery, logTypeName) = logType.toLowerCase match {
        case "error" => (Map("event_name" -> Map($regex -> "^ERROR.+")), "Error")
        case "audit" => (Map("event_name" -> Map($regex -> "^AUDIT.+")), "Audit")
        case "check" => (Map("event_name" -> Map($regex -> "^(AUDIT|ERROR).+")), "Check")
        case "full" => (Map.empty[String, AnyRef], "Full")
        case "any" | _ => (Map("event_name" -> Map($regex -> "^(AUDIT|ENTRY|ERROR|EXIT).+")), "Any")
      }
      writer.println(s"""<body><h2 align="center">$logTypeName Log ($duration $durationUnit)</h2>""")
      writer.println("<table border=\"1\" style=\"width: 100%;\">")
      val widths = Seq(10, 10, 10, 10, 10, 2, 34, 34)
      writer.println(List("Timestamp", "Process", "Session", "User", "IP", "Activity", "Event", "Variables").zip(widths).
          map(p => s"""<td style="width: ${p._2}%;" align="center">${p._1}</td>""").
          mkString("<tr bgcolor=\"cyan\">", "", "</tr>"))

      val millisNow = System.currentTimeMillis
      val (untilStr, untilMs, untilUnit, untilNum) = parameters.get("until") match {
        case None => ("now", millisNow, "", millisNow)
        case Some(input) =>
          val withHourPattern = "(\\d+)hours".r
          val withDayPattern = "(\\d+)days".r
          val withMsPattern = "(\\d{10,})".r
          input match {
            case withHourPattern(hrs) => (s"${hrs}hours", millisNow - 3600L * 1000L * hrs.toLong, "hours", hrs.toLong)
            case withDayPattern(dys) => (s"${dys}days", millisNow - 86400L * 1000L * dys.toLong, "days", dys.toLong)
            case withMsPattern(d) => (d, d.toLong, "ms", d)
            case _ => ("now", millisNow, "", millisNow)
          }
      }
      val traceLogCollection = BWMongoDB3.trace_log
      val grouper = Map("$group" ->
          Map("_id" -> Map("$subtract" -> Seq("$milliseconds", Map("$mod" -> Seq("$milliseconds", 900000L)))),
          "details" -> Map($push -> Map("event" -> "$event_name", "process" -> "$process_id", "variables" -> "$variables",
          "milliseconds" -> Map("$toString" -> "$milliseconds"), "activity" -> "$activity_name"))))

      val traceLogDocs: Seq[DynDoc] = durationUnit match {
        case "hours" =>
          val startMs = untilMs - 3600L * 1000L * duration
          val pipeline: Seq[Document] = Seq(
            Map("$match" -> (typeQuery ++ Map("milliseconds" -> Map("$gte" -> startMs, "$lte" -> untilMs)))),
            grouper,
            Map("$sort" -> Map("_id" -> -1))
          )
          traceLogCollection.aggregate(pipeline.asJava).allowDiskUse(true)
        case "days" =>
          val startMs = untilMs - 86400L * 1000L * duration
          val pipeline: Seq[Document] = Seq(
            Map("$match" -> (typeQuery ++ Map("milliseconds" -> Map("$gte" -> startMs, "$lte" -> untilMs)))),
            grouper,
            Map("$sort" -> Map("_id" -> -1))
          )
          traceLogCollection.aggregate(pipeline.asJava).allowDiskUse(true)
        case _ =>
          val pipeline: Seq[Document] = Seq(
            Map("$match" -> (typeQuery ++ Map("milliseconds" -> Map("$lte" -> untilMs)))),
            Map("$sort" -> Map("milliseconds" -> -1)),
            Map("$limit" -> 50),
            grouper
          )
          traceLogCollection.aggregate(pipeline.asJava).allowDiskUse(true)
      }

      val fullName = PersonApi.fullName(user)
      val details: Seq[DynDoc] = traceLogDocs.flatMap(_.details[Many[Document]])
      details.sortBy(_.milliseconds[String]).reverse.foreach(detail => {
        val activity = detail.activity[String]
        val variables: Seq[(String, AnyRef)] = detail.get[Document]("variables") match {
          case Some(doc) => doc.asScala.toSeq
          case None => Seq.empty[(String, AnyRef)]
        }
        val variablesString = variables.filter(v => v._1 != "BW-Session-ID" && v._1 != "u$nm").
            map(p => s"${p._1}: ${p._2}").mkString(", ")
        val session = variables.find(_._1 == "BW-Session-ID") match {
          case Some(sessionId) => sessionId._2.hashCode().toString
          case None => variables.find(_._1 == "BW-Session-Code") match {
            case Some(sessionCode) => sessionCode._2
            case None => ""
          }
        }
        val user = variables.find(_._1 == "u$nm") match {
          case Some(un) => un._2
          case None => ""
        }
        val process = detail.process[String]
        val rawEvent = addSpaces(detail.event[String])
        val reverseEventPatternMatcher = reverseEventPattern.matcher(rawEvent.reverse)
        val (ip, event) = if (reverseEventPatternMatcher.matches()) {
          (reverseEventPatternMatcher.group(1).reverse, reverseEventPatternMatcher.group(2).reverse.trim())
        } else {
          ("127.0.0.1", rawEvent)
        }
        val timestamp = dateTimeString(detail.milliseconds[String].toLong, parameters.get("tz").orElse(Some("Asia/Calcutta")))
        val color = if (event.toLowerCase.contains("error")) "red" else "black"
        val htmlRowData = Seq(timestamp, process, session, user, ip, activity, event, variablesString).zip(widths).
            map(dd => s"""<td style="width: ${dd._2}%">${dd._1}</td>""").mkString
        if (htmlRowData.contains(clientIp) || htmlRowData.contains(s"u$$nm: $fullName"))
          writer.println(s"""<tr style="background-color: beige;color: $color" align="center">$htmlRowData</tr>""")
        else
          writer.println(s"""<tr style="color: $color" align="center">$htmlRowData</tr>""")
      })
      if (traceLogDocs.isEmpty)
        writer.println(s"""<tr><td colspan="5" align="center">No such rows!</td></tr>""")
      writer.println("</table>")

      val hourCountLinks = Seq(1, 2, 6, 18).
          map(n => if (durationUnit == "hours" && duration == n) s"($n)" else n).
          map(n => s"""<a href="$urlName?count=${n}hours&type=$logType&until=$untilStr">$n</a>""").
          mkString("&nbsp;&nbsp;")
      val dayCountLinks = Seq(1, 2, 7, 14, 28).
          map(n => if (durationUnit == "days" && duration == n) s"($n)" else n).
          map(n => s"""<a href="$urlName?count=${n}days&type=$logType&until=$untilStr">$n</a>""").
          mkString("&nbsp;&nbsp;")
      val rowCountLinks = Seq(50, 100, 200, 500).
          map(n => if (durationUnit == "rows" && duration == n) s"($n)" else n).
          map(n => s"""<a href="$urlName?count=${n}rows&type=$logType&until=$untilStr">$n</a>""").
          mkString("&nbsp;&nbsp;")

      writer.println(s"""<h3 align=\"center\">DURATION: &nbsp;&nbsp;&nbsp;&nbsp;hours: $hourCountLinks,
                        |&nbsp;&nbsp;&nbsp;&nbsp;days: $dayCountLinks,
                        |&nbsp;&nbsp;&nbsp;&nbsp;rows: $rowCountLinks</h3>""".stripMargin)

      val typeLinks = Seq("any", "audit", "error", "check", "full").
          map(t => if (logType == t) s"($t)" else t).
          map(t => s"""<a href="$urlName?count=$duration$durationUnit&type=$t&until=$untilStr">$t</a>""").
          mkString("&nbsp;&nbsp;")
      writer.println(s"""<h3 align=\"center\">Log-type: $typeLinks</h3>""")

      val hoursUntilLinks = Seq(1, 2, 3, 6, 12, 18).
          map(hr => if (untilUnit == "hours" && untilNum == hr) s"($hr)" else hr).
          map(hr => s"""<a href="$urlName?count=$duration$durationUnit&type=$logType&until=${hr}hours">$hr</a>""").
          mkString("&nbsp;&nbsp;")
      val daysUntilLinks = Seq(1, 2, 3, 7, 14, 28).
          map(dy => if (untilUnit == "days" && untilNum == dy) s"($dy)" else dy).
          map(days => s"""<a href="$urlName?count=$duration$durationUnit&type=$logType&until=${days}days">$days</a>""").
          mkString("&nbsp;&nbsp;")
      writer.println(s"""<h3 align=\"center\">UNTIL: &nbsp;&nbsp;&nbsp;&nbsp;hours: $hoursUntilLinks,
                        |&nbsp;&nbsp;&nbsp;&nbsp;days: $daysUntilLinks</h3>""".stripMargin)

      writer.println("</body></html>")
      response.setStatus(HttpServletResponse.SC_OK)
    } catch {
      case t: Throwable => t.printStackTrace(writer)
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
    }
  }

//  private def prettyPrint(d: Document): String = {
//    d.asScala.map(p => s"${p._1}: ${p._2}").mkString(", ")
//  }
//
}