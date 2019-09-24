package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils, DateTimeUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class ProcessStartDateSet extends HttpServlet with HttpUtils with DateTimeUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      val processOid = new ObjectId(parameters("process_id"))
      if (!ProcessApi.exists(processOid))
        throw new IllegalArgumentException(s"Bad process_id: $processOid")
      val theProcess: DynDoc = ProcessApi.processById(processOid)
      if (!ProcessApi.canManage(userOid, theProcess))
        throw new IllegalArgumentException("No permission")
      if (theProcess.status[String] != "defined")
        throw new IllegalArgumentException("Wrong state")
      val dateTime = milliseconds(parameters("datetime"))
      val updateResult = BWMongoDB3.processes.updateOne(Map("_id" -> processOid),
        Map($set -> Map(s"timestamps.planned_start" -> dateTime)))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      response.setStatus(HttpServletResponse.SC_OK)
      val message = s"'${theProcess.name[String]}' ($processOid)"
      BWLogger.audit(getClass.getName, "doPost", s"""Set start-date of process $message""", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
