package com.buildwhiz.etc

import com.buildwhiz.api.RestUtils
import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._

import org.bson.Document

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class Status extends HttpServlet with RestUtils {

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
      "count" -> Map("$sum" -> 1),
      "errors" -> Map("$sum" -> Map("$cond" -> Map("if" -> isError, "then" -> 1, "else" -> 0)))
    ))
    output.asDoc
  }

  private def sendStatusPage(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val t0 = System.currentTimeMillis()
    response.setContentType("text/html")
    val writer = response.getWriter
    val (duration, pitch) = (240, 15)
    val logInfo: Seq[DynDoc] = BWMongoDB3.trace_log.
        aggregate(Seq(matcher(duration, "Anon Anon"), grouper(pitch)))
    writer.println("""<html><body><table border="1" align="center">""")
    writer.println(s"<tr><td>ID</td><td>Count</td><td>Errors</td></tr>")
    for (info <- logInfo) {
      val (id, count, errors) = (info._id[Double], info.count[Int], info.errors[Int])
      writer.println(s"<tr><td>$id</td><td>$count</td><td>$errors</td></tr>")
    }
    val delay = System.currentTimeMillis() - t0
    writer.println(s"""<tr><td colspan="3">time=$delay</td></tr>""")
    writer.println("</table></body></html>")
  }

  private def sendStatus(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val t0 = System.currentTimeMillis()
    response.setContentType("application/json")
    val user: DynDoc = getPersona(request)
    val userName = if (user.asDoc == null) {
      "^Sanjay (Dasgupta|Admin).*$"
    } else {
      "^" + PersonApi.fullName(user) + ".*$"
    }
    try {
      val logInfo: Seq[DynDoc] = BWMongoDB3.trace_log.aggregate(Seq(matcher(60, userName), grouper(1)))
      val under60 = logInfo.filter(_._id[Double] > 30).map(_.count[Int]).sum
      val under30 = logInfo.filter(info => info._id[Double] <= 30 && info._id[Double] > 15).map(_.count[Int]).sum
      val under15 = logInfo.filter(info => info._id[Double] <= 15 && info._id[Double] > 5).map(_.count[Int]).sum
      val under5 = logInfo.filter(_._id[Double] <= 5).map(_.count[Int]).sum
      val totalCount = logInfo.map(_.count[Int]).sum
      val errorCount = logInfo.map(_.errors[Int]).sum
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
    if (parameters.contains("detail")) {
      sendStatusPage(request, response)
    } else {
      sendStatus(request, response)
    }
    //BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
  }

}