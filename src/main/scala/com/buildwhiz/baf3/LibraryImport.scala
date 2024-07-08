package com.buildwhiz.baf3

import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.collection.mutable

class LibraryImport extends HttpServlet with HttpUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val parameters = getParameterMap(request)
      val phaseOid = new ObjectId(parameters("phase_id"))
      val projectOid = new ObjectId(parameters("project_id"))
      val outputBuffer = new mutable.ArrayBuffer[String]()
      response.setContentType("application/json")
      try {
        LibraryOperations.importPhase(phaseOid, projectOid, msg => outputBuffer.append(msg), request)
        response.getWriter.print(successJson(fields = Map("html" -> outputBuffer.mkString("\n"))))
        BWLogger.audit(getClass.getName, request.getMethod, s"Phase '$phaseOid' imported into project '$projectOid' OK",
            request)
      } catch {
        case t: Throwable =>
          val returnJson = new Document("ok", 0).append("message", "See details in log")
          response.getWriter.print(returnJson)
          val messages = t.getStackTrace.map(_.toString).filter(_.contains("com.buildwhiz")).mkString
          BWLogger.log(getClass.getName, request.getMethod, s"EXIT-ERROR: $messages", request)
      }
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}