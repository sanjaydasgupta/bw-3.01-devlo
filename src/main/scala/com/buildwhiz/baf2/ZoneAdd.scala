package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class ZoneAdd extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {

      val phaseOid = new ObjectId(parameters("phase_id"))
      if (!PhaseApi.exists(phaseOid))
        throw new IllegalArgumentException(s"Unknown phase-id: '$phaseOid'")

      val zoneName = parameters("zone_name")

      val parentProject = PhaseApi.parentProject(phaseOid)
      val projectOid = parentProject._id[ObjectId]

      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      if (!PhaseApi.canManage(userOid, PhaseApi.phaseById(phaseOid)))
        throw new IllegalArgumentException("Not permitted")

      val queryRecord = Map("name" -> zoneName, "project_id" -> projectOid, "phase_id" -> phaseOid)
      if (BWMongoDB3.zones.countDocuments(queryRecord) > 0)
        throw new IllegalArgumentException(s"Zone '$zoneName' already exists")

      val area = parameters("area")
      val location = parameters("location")
      val description = parameters("description")

      val newZoneRecord: Document = queryRecord ++ Map("activity_ids" -> Seq.empty[ObjectId].asJava,
          "area" -> area, "location" -> location, "description" -> description)
      BWMongoDB3.zones.insertOne(newZoneRecord)

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