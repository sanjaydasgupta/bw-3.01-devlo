package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.PhaseApi
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

object PhaseDelete {

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    response.getWriter.println(s"${getClass.getName}:main() ENTRY")
    if (args.length >= 1) {
      val go: Boolean = args.length == 2 && args(1) == "GO"
      val phaseOid = new ObjectId(args(0))
      BWMongoDB3.phases.find(Map("_id" -> phaseOid)).headOption match {
        case None =>
          response.getWriter.println(s"No such phase ID: '${args(0)}'")
        case Some(thePhase) =>
          response.getWriter.println(s"Found phase: '${thePhase.asDoc.toJson}'")
          if (go) {
            response.getWriter.println(s"DELETING phase ...")
            PhaseApi.delete(thePhase, request)
            response.getWriter.println(s"DELETE phase complete")
          }
      }
      response.getWriter.println(s"${getClass.getName}:main() EXIT-OK")
    } else {
      response.getWriter.println(s"${getClass.getName}:main() EXIT-ERROR Usage: ${getClass.getName} phase-id [,GO]")
    }
  }

}
