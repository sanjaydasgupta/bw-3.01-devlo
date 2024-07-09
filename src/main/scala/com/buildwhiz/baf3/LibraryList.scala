package com.buildwhiz.baf3

import com.buildwhiz.baf2.ActivityApi.dateTimeStringAmerican
import com.buildwhiz.infra.{BWMongoDBLib, DynDoc}
import com.buildwhiz.infra.BWMongoDBLib._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class LibraryList extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      response.setContentType("application/json")
      try {
        val phases: Seq[DynDoc] = BWMongoDBLib.phases.find()
        val phaseList: Seq[DynDoc] = phases.map(phase => {
          val libInfo: DynDoc = phase.library_info[Document]
          val timestamp = libInfo.timestamp[Long]
          val dateTime = dateTimeStringAmerican(timestamp, Some(user.tz[String]))
          Map("name" -> phase.name[String], "user" -> libInfo.user[String], "timestamp" -> dateTime,
            "original_partner" -> libInfo.original_partner[String], "instance_name" -> libInfo.instance_name[String])
        })
        val retJson: String = phaseList.map(_.asDoc.toJson).mkString("[", ", ", "]")
        response.getWriter.println(retJson)
        BWLogger.audit(getClass.getName, request.getMethod, s"EXIT-OK", request)
      } catch {
        case t: Throwable =>
          val returnJson = new Document("ok", 0).append("message", "See details in log")
          response.getWriter.print(returnJson)
          val messages = t.getStackTrace.map(_.toString).filter(_.contains("at com.buildwhiz.")).mkString("\n")
          BWLogger.log(getClass.getName, request.getMethod, s"EXIT-ERROR: $messages", request)
      }
    } catch {
      case t: Throwable =>
        t.getStackTrace
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}