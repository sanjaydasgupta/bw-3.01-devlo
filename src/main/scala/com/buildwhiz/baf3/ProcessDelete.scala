package com.buildwhiz.baf3

import com.buildwhiz.baf2.ProcessApi
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class ProcessDelete extends HttpServlet with HttpUtils {

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
