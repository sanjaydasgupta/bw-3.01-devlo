package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.{PersonApi, PhaseApi}
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.HttpUtils
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

object PhaseDelete extends HttpUtils {

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    val writer = response.getWriter
    def output(s: String): Unit = writer.print(s)
    try {
      output(s"${getClass.getName}:main() ENTRY")
      val user: DynDoc = getUser(request)
      if (!PersonApi.isBuildWhizAdmin(Right(user)) || user.first_name[String] != "Sanjay") {
        throw new IllegalArgumentException("Not permitted")
      }
      if (args.length >= 1) {
        val go: Boolean = args.length == 2 && args(1) == "GO"
        val phaseOid = new ObjectId(args(0))
        BWMongoDB3.phases.find(Map("_id" -> phaseOid)).headOption match {
          case None =>
            output(s"No such phase ID: '${args(0)}'")
          case Some(thePhase) =>
            output(s"Found phase: '${thePhase.asDoc.toJson}'")
            if (go) {
              output(s"DELETING phase ...")
              PhaseApi.delete(thePhase, request)
              output(s"DELETE phase complete")
            }
        }
        output(s"${getClass.getName}:main() EXIT-OK")
      } else {
        output(s"${getClass.getName}:main() EXIT-ERROR Usage: ${getClass.getName} phase-id [,GO]")
      }
    } catch {
      case t: Throwable =>
        output(t.getStackTrace.map(_.toString).mkString("\n"))
    }
  }

}
