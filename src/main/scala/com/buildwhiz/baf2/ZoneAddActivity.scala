package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId

class ZoneAddActivity extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      val parameters = getParameterMap(request)

      val zoneOid = new ObjectId(parameters("zone_id"))
      if (!ZoneApi.exists(zoneOid))
        throw new IllegalArgumentException(s"Bad zone_id '$zoneOid'")
      val theZone = ZoneApi.zoneById(zoneOid)

      val activityOid = new ObjectId(parameters("activity_id"))
      if (!ActivityApi.exists(activityOid))
        throw new IllegalArgumentException(s"Bad activity_id '$activityOid'")
      val theActivity = ActivityApi.activityById(activityOid)

      val parentProcess = ActivityApi.parentProcess(activityOid)
      val parentPhase = ProcessApi.parentPhase(parentProcess._id[ObjectId])

      if (parentPhase._id[ObjectId] != theZone.phase_id[ObjectId])
        throw new IllegalArgumentException("Activity and zone in different phases")

      val updateResult = BWMongoDB3.zones.updateOne(Map("_id" -> zoneOid),
          Map($addToSet -> Map("activity_ids" -> activityOid)))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

      response.setStatus(HttpServletResponse.SC_OK)
      val message = s"Added activity '${theActivity.name[String]}' to zone '${theZone.name[String]}'"
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}