package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.{PersonApi, ProcessApi}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.HttpUtils
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

object ProcessDelete extends HttpUtils {

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    response.getWriter.println(s"${getClass.getName}:main() ENTRY")
    val user: DynDoc = getUser(request)
    if (!PersonApi.isBuildWhizAdmin(Right(user)) || user.first_name[String] != "Sanjay") {
      throw new IllegalArgumentException("Not permitted")
    }
    if (args.length >= 1) {
      val go: Boolean = args.length == 2 && args(1) == "GO"
      val processOid = new ObjectId(args(0))
      BWMongoDB3.processes.find(Map("_id" -> processOid)).headOption match {
        case None =>
          response.getWriter.println(s"No such process ID: '${args(0)}'")
        case Some(theProcess) =>
          response.getWriter.println(s"Found process: '${theProcess.asDoc.toJson}'")
          if (go) {
            response.getWriter.println(s"DELETING process ...")
            ProcessApi.delete(theProcess, request)
            response.getWriter.println(s"DELETE process complete")
          }
      }
      response.getWriter.println(s"${getClass.getName}:main() EXIT-OK")
    } else {
      response.getWriter.println(s"${getClass.getName}:main() EXIT-ERROR Usage: ${getClass.getName} process-id [,GO]")
    }
  }

}
