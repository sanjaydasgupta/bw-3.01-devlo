package com.buildwhiz.etc

import com.buildwhiz.api.RestUtils
import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.utils.DateTimeUtils
import org.bson.Document

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class Status extends HttpServlet with RestUtils with DateTimeUtils {

  private def matcher(durationInMinutes: Int, userName: String): Document = {
    val output: DynDoc = Map("$match" -> Map(
      "milliseconds" -> Map($gte -> (System.currentTimeMillis - durationInMinutes * 60000L)),
      "process_id" -> Map($ne -> "baf2.Login"),
      "event_name" -> Map($regex -> "^(EXIT[ -]|ERROR:).*",
        $not -> Map($regex -> ".*(BuildWhiz: Not logged in|Authentication failed).*")),
      "variables.u$nm" -> Map($not -> Map($regex -> userName))
    ))
    output.asDoc
  }

  private def grouper(pitchInMinutes: Int): Document = {
    val ageInMs = Map("$subtract" -> Seq(System.currentTimeMillis, "$milliseconds"))
    val isError = Map("$regexMatch" -> Map("input" -> "$event_name", "regex" -> "^(ERROR:|EXIT-ERROR).+"))
    val output: DynDoc = Map("$group" -> Map(
      "_id" -> Map("$ceil" -> Map("$divide" -> Seq(ageInMs, pitchInMinutes * 60000))),
      "users" -> Map("$push" -> "$variables.u$nm"),
      "errors" -> Map("$push" -> Map("$cond" -> Map("if" -> isError, "then" -> 1, "else" -> 0)))
    ))
    output.asDoc
  }

  private val sortOnId: Document = Map("$sort" -> Map("_id" -> 1))

  private def sendStatusPage(tz: String, userName: String, detail: String, response: HttpServletResponse): Unit = {
    def cleanUserName(userName: String): String = {
      val userNameParts = userName.split("\\s+")
      if (userNameParts.last.matches("\\([0-9a-f]{24}\\)")) {
        userNameParts.init.mkString(" ")
      } else {
        userNameParts.mkString(" ")
      }
    }

    val t0 = System.currentTimeMillis()
    response.setContentType("text/html")
    val writer = response.getWriter
    val daysRe = "([0-9]+)d".r
    val hoursRe = "([0-9]+)h?".r
    val (duration, pitch) = detail match {
      case daysRe(days) => (days.toInt * 60 * 24, 60)
      case hoursRe(hours) => (hours.toInt * 60, 15)
      case _ => (240, 15)
    }
    val logInfo: Seq[DynDoc] = BWMongoDB3.trace_log.
        aggregate(Seq(matcher(duration, userName), grouper(pitch), sortOnId))
    writer.println("""<html><body><table border="1">""")
    writer.println("""<tr><td align="center">Time</td><td align="center">Users</td></tr>""")
    for (info <- logInfo) {
      val (id, users, errors) = (info._id[Double], info.users[Many[String]], info.errors[Many[Int]])
      val ms = t0 - id.toLong * pitch * 60000L
      val time = dateTimeString(ms, Some(tz))
      val userDetail = users.zip(errors).groupBy(p => cleanUserName(p._1)).map(p => {
        val name = p._1
        val totalCount = p._2.length
        val errorCount = p._2.map(_._2).sum
        s"$name ($totalCount:$errorCount)"
      })
      writer.println(s"""<tr><td>$time</td><td align="center">${userDetail.mkString(", ")}</td></tr>""")
    }
    val delay = System.currentTimeMillis() - t0
    writer.println(s"""<tr><td colspan="2" align="center">time=$delay</td></tr>""")
    writer.println("</table></body></html>")
  }

  private def sendStatus(userName: String, response: HttpServletResponse): Unit = {
    val t0 = System.currentTimeMillis()
    response.setContentType("application/json")
    try {
      val logInfo: Seq[DynDoc] = BWMongoDB3.trace_log.aggregate(Seq(matcher(60, userName), grouper(1), sortOnId))
      val under60 = logInfo.filter(_._id[Double] > 30).map(_.users[Many[String]].size()).sum
      val under30 = logInfo.filter(info => info._id[Double] <= 30 && info._id[Double] > 15).
          map(_.users[Many[String]].size()).sum
      val under15 = logInfo.filter(info => info._id[Double] <= 15 && info._id[Double] > 5).
          map(_.users[Many[String]].size()).sum
      val under5 = logInfo.filter(_._id[Double] <= 5).map(_.users[Many[String]].size()).sum
      val totalCount = logInfo.map(_.users[Many[String]].size()).sum
      val errorCount = logInfo.map(_.errors[Many[Int]].sum).sum
      val delay = System.currentTimeMillis() - t0
      val fields: DynDoc = Map("total" -> totalCount, "bad" -> errorCount, "under60" -> under60, "under30" -> under30,
        "under15" -> under15, "under5" -> under5, "time" -> delay)
      response.getWriter.println(fields.asDoc.toJson)
    } catch {
      case t: Throwable =>
        response.getWriter.println(s"""{"total": -1, "errors": -1, "error_minutes": 0}""")
        throw t
    }
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    //BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    val user = getPersona(request)
    val userName = if (user == null) {
      "^Sanjay (Dasgupta|Admin).*$"
    } else {
      "^" + PersonApi.fullName(user) + ".*$"
    }
    (parameters.get("detail"), parameters.get("tz")) match {
      case (Some(detail), Some(tz)) => sendStatusPage(tz, userName, detail, response)
      case (Some(detail), None) =>
        val tz = if (user == null) {
          "GMT"
        } else {
          user.get("tz").asInstanceOf[String]
        }
        sendStatusPage(tz, userName, detail, response)
      case (None, _) => sendStatus(userName, response)
    }
    //BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
  }

}