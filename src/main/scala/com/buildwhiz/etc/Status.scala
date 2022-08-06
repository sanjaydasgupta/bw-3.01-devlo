package com.buildwhiz.etc

import com.buildwhiz.api.RestUtils
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class Status extends HttpServlet with RestUtils {

  private def sendStatus(response: HttpServletResponse): Unit = {
    response.setContentType("application/json")
    try {
      val matcher: DynDoc = Map("$match" -> Map(
        "milliseconds" -> Map($gte -> (System.currentTimeMillis - 60L * 60 * 1000)),
        "event_name" -> Map($regex -> "^(EXIT[ -]|ERROR:).*"),
        "process_id" -> Map($not -> Map($regex -> "baf2.Login")),
        "variables.u$nm" -> Map($not -> Map($regex -> "^Sanjay.*"))
      ))
      val diff = Map("$subtract" -> Seq(System.currentTimeMillis, "$milliseconds"))
      val grouper: DynDoc = Map("$group" -> Map(
        "_id" -> Map("$ceil" -> Map("$divide" -> Seq(diff, 60000))),
        "count" -> Map("$sum" -> 1),
        "errors" -> Map("$sum" -> Map("$cond" -> Map("if" -> Map("$regexMatch" -> Map("input" -> "$event_name", "regex" -> "^(ERROR:|EXIT-ERROR).+")), "then" -> 1, "else" -> 0)))
      ))
      val logInfo: Seq[DynDoc] = BWMongoDB3.trace_log.aggregate(Seq(matcher.asDoc, grouper.asDoc))
      val under60 = logInfo.filter(_._id[Double] > 30).map(_.count[Int]).sum
      val under30 = logInfo.filter(info => info._id[Double] <= 30 && info._id[Double] > 15).map(_.count[Int]).sum
      val under15 = logInfo.filter(info => info._id[Double] <= 15 && info._id[Double] > 5).map(_.count[Int]).sum
      val under5 = logInfo.filter(_._id[Double] <= 5).map(_.count[Int]).sum
      val totalCount = logInfo.map(_.count[Int]).sum
      val errorCount = logInfo.map(_.errors[Int]).sum
      val fields: DynDoc = Map("total" -> totalCount, "bad" -> errorCount, "under60" -> under60, "under30" -> under30,
        "under15" -> under15, "under5" -> under5)
      response.getWriter.println(fields.asDoc.toJson)
    } catch {
      case t: Throwable =>
        response.getWriter.println(s"""{"total": -1, "errors": -1, "error_minutes": 0}""")
        throw t
    }
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    //BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)

    sendStatus(response)
    //BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
  }

}