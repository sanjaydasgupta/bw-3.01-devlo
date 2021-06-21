package com.buildwhiz.baf3

import com.buildwhiz.baf2.{PersonApi, ProjectApi}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.slack.SlackApi
import com.buildwhiz.utils.{BWLogger, HttpUtils, DateTimeUtils}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.collection.JavaConverters._

class ProjectInfoSet extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val parameterString = getStreamData(request)
      BWLogger.log(getClass.getName, request.getMethod, s"Parameter-String: $parameterString", request)
      val postData = Document.parse(parameterString)
      if (!postData.containsKey("project_id"))
        throw new IllegalArgumentException("project_id not provided")
      val projectId = postData.remove("project_id").asInstanceOf[String]
      val nameValuePairs = postData.entrySet.asScala.map(es => (es.getKey, es.getValue)).toSeq
      response.getWriter.print(successJson())
      response.setContentType("application/json")
      val message = ProjectInfoSet.setProjectFields(projectId, nameValuePairs, request)
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}

object ProjectInfoSet extends DateTimeUtils {

  def managers2roles(projectOid: ObjectId, mids: String): Many[Document] = {
    val projMgrOids: Seq[ObjectId] = mids.split(",").map(_.trim).filter(_.nonEmpty).
        distinct.map(new ObjectId(_))
    val badManagerIds = projMgrOids.filterNot(PersonApi.exists)
    if (badManagerIds.nonEmpty)
      throw new IllegalArgumentException(s"""Bad project_manager_ids: ${badManagerIds.mkString(", ")}""")
    val projectRecord = ProjectApi.projectById(projectOid)
    val existingNonManagerRoles: Seq[DynDoc] = projectRecord.assigned_roles[Many[Document]].
        filterNot(_.role_name[String].matches("(?i)Project-Manager"))
    val newRoles: Seq[DynDoc] = projMgrOids.map(oid => Map("role_name" -> "Project-Manager", "person_id" -> oid))
    (existingNonManagerRoles ++ newRoles).map(_.asDoc).asJava
  }

  private val fullNames: Map[String, String] = Map(
    ("name", "name"), ("summary", "summary"), ("goals", "goals"), ("description", "description"),
    ("state_name", "address.state.name"), ("country_name", "address.country.name"),
    ("gps_latitude", "address.gps_location.latitude"), ("gps_longitude", "address.gps_location.longitude"),
    ("address_line1", "address.line1"), ("address_line2", "address.line2"), ("address_line3", "address.line3"),
    ("postal_code", "address.postal_code"), ("customer", "customer_organization_id"),
    ("construction_type", "construction_type"), ("type", "type"), ("project_type", "type"),
    ("budget_mm_usd", "budget_mm_usd"), ("budget", "budget_mm_usd"), ("building_use", "building_use"),
    ("construction_area_sqft", "construction_area_sqft"), ("building_footprint", "construction_area_sqft"),
    ("land_area_acres", "land_area_acres"), ("site_area", "land_area_acres"),
    ("max_building_height_ft", "max_building_height_ft"), ("building_height", "max_building_height_ft"),
    ("total_floor_area", "total_floor_area"), ("tz", "tz"),
    ("project_id", "project_id"), ("project_managers", "assigned_roles"), ("phase_info", "phase_info")
  )

  private val converters: Map[String, (ObjectId, String) => Any] = Map(
    "project_managers" -> managers2roles, "customer" -> ((_, id) => new ObjectId(id))
  )

  def setProjectFields(projectId: String, nameValuePairs: Seq[(String, AnyRef)], request: HttpServletRequest,
                       doLog: Boolean = false): String = {
    if (doLog)
      BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    if (nameValuePairs.isEmpty)
      throw new IllegalArgumentException("No parameters found")
    val unknownParameters = nameValuePairs.map(_._1).filterNot(fullNames.containsKey)
    if (unknownParameters.nonEmpty)
      throw new IllegalArgumentException(s"""Unknown parameter(s): ${unknownParameters.mkString(", ")}""")
    nameValuePairs.find(_._1 == "name") match {
      case Some((_, name)) =>
        ProjectApi.validateNewName(name.toString)
      case None =>
    }
    val (phaseInfo, projectNameValuePairs) = nameValuePairs.partition(_._1 == "phase_info")
    val projectOid = new ObjectId(projectId)
    if (phaseInfo.nonEmpty) {
      val phaseInfoArray: Seq[DynDoc] = phaseInfo.head._2.asInstanceOf[Many[Document]]
      for (phaseInfo <- phaseInfoArray) {
        val phaseOid = new ObjectId(phaseInfo._id[String])
        val mongoDbSetters: Document = (phaseInfo.get[String]("start_date"), phaseInfo.get[String]("end_date")) match {
          case (Some(startDate), None) => Map("timestamps.date_start_estimated" -> milliseconds(startDate))
          case (None, Some(endDate)) => Map("timestamps.date_end_estimated" -> milliseconds(endDate))
          case (Some(startDate), Some(endDate)) => Map("timestamps.date_start_estimated" -> milliseconds(startDate),
            "timestamps.date_end_estimated" -> milliseconds(endDate))
          case (None, None) =>
            throw new IllegalArgumentException(s"No dates for phase: $phaseOid")
        }
        val updateResult = BWMongoDB3.phases.updateOne(Map("_id" -> phaseOid), Map("$set" -> mongoDbSetters))
        if (updateResult.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
      }
    }
    if (projectNameValuePairs.nonEmpty) {
      val mongoDbSetters = projectNameValuePairs.map(p =>
        if (converters.contains(p._1))
          (fullNames(p._1), converters(p._1)(projectOid, p._2.toString))
        else
          (fullNames(p._1), p._2)
      ).toMap
      val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid), Map("$set" -> mongoDbSetters))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
    }
    val parametersChanged = nameValuePairs.map(_._1).mkString("[", ", ", "]")
    val message = s"""Updated parameters $parametersChanged of project $projectOid"""
    val managers = ProjectApi.managers(Left(projectOid))
    for (manager <- managers) {
      SlackApi.sendNotification(message, Right(manager), Some(projectOid), Some(request))
    }
    if (doLog)
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    message
  }

}