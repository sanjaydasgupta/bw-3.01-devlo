package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class ZoneInfo extends HttpServlet with HttpUtils with DateTimeUtils {

  private def isEditable(zone: DynDoc, user: DynDoc): Boolean = {
    val userOid = user._id[ObjectId]
    ZoneApi.canManage(userOid, zone)
  }

  private def activityInformation(zone: DynDoc, user: DynDoc): Many[Document] = {
    val activities: Seq[DynDoc] = ZoneApi.allActivities(zone)
    val returnValue: Seq[Document] = activities.map(activity => {
      val timestamps: DynDoc = activity.timestamps[Document]
      val timeZone = user.tz[String]
      val (startTime, endTime) = if (timestamps.has("start")) {
        if (timestamps.has("end")) {
          (dateTimeString(timestamps.start[Long], Some(timeZone)), dateTimeString(timestamps.end[Long], Some(timeZone)))
        } else {
          (dateTimeString(timestamps.start[Long], Some(timeZone)), "NA")
        }
      } else {
        ("NA", "NA")
      }
      Map("_id" -> activity._id[ObjectId].toString, "name" -> activity.name[String],
          "bpmn_name" -> activity.bpmn_name[String], "status" -> activity.status[String],
          "start_date" -> startTime, "end_date" -> endTime)
    })
    returnValue.asJava
  }

  private def zone2json(zone: DynDoc, user: DynDoc, editable: Boolean): String = {
    val description = new Document("editable", editable).append("value", zone.description[String])
    val location = new Document("editable", editable).append("value", zone.location[String])
    val area = new Document("editable", editable).append("value", zone.area[String])
    val name = new Document("editable", editable).append("value", zone.name[String])
    val oid = zone._id[ObjectId].toString

    val zoneDoc = new Document("name", name).append("_id", oid).append("description", description).
        append("location", location).append("area", area).append("activity_info", activityInformation(zone, user))
    zoneDoc.toJson
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val zoneOid = new ObjectId(parameters("zone_id"))
      val zoneRecord: DynDoc = ZoneApi.zoneById(zoneOid)
      val user: DynDoc = getUser(request)
      val zoneIsEditable = isEditable(zoneRecord, user)
      response.getWriter.print(zone2json(zoneRecord, user, zoneIsEditable))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}