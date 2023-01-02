package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.{PersonApi, ProcessApi}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

object ProcessDelete extends HttpServlet with HttpUtils {

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
            processDelete(processOid) match {
              case Right(msg) =>
                response.getWriter.println(msg)
                response.getWriter.println(s"DELETE process complete")
              case Left(msg) =>
                response.getWriter.println(msg)
                response.getWriter.println(s"DELETE process failed")
            }
          }
      }
      response.getWriter.println(s"${getClass.getName}:main() EXIT-OK")
    } else {
      response.getWriter.println(s"${getClass.getName}:main() EXIT-ERROR Usage: ${getClass.getName} process-id [,GO]")
    }
  }

  private def processDelete(processOid: ObjectId): Either[String, String] = {
    BWMongoDB3.processes.find(Map("_id" -> processOid)).headOption match {
      case None =>
        Left(s"Bad process_id: '$processOid'")
      case Some(theProcess) =>
        ProcessApi.delete(theProcess)
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    try {
      BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
      val parameters = getParameterMap(request)
      val processOid = new ObjectId(parameters("process_id"))
      processDelete(processOid) match {
        case Right(msg) =>
          BWLogger.audit(getClass.getName, request.getMethod, msg, request)
        case Left(msg) =>
          BWLogger.log(getClass.getName, request.getMethod, s"EXIT-ERROR: $msg", request)
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
