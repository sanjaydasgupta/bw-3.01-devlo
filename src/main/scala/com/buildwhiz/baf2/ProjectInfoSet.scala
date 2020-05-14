package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.slack.SlackApi
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class ProjectInfoSet extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    try {
      val postData = Document.parse(getStreamData(request))
      if (!postData.containsKey("project_id"))
        throw new IllegalArgumentException("project_id not provided")
      val projectId = postData.remove("project_id").asInstanceOf[String]
      val nameValuePairs = postData.entrySet.asScala.map(es => (es.getKey, es.getValue.asInstanceOf[String])).toSeq
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

object ProjectInfoSet {

  private val fullNames: Map[String, String] = Map(
    ("name", "name"), ("description", "description"),
    ("state_name", "address.state.name"), ("country_name", "address.country.name"),
    ("gps_latitude", "address.gps_location.latitude"), ("gps_longitude", "address.gps_location.longitude"),
    ("address_line1", "address.line1"), ("address_line2", "address.line2"), ("address_line3", "address.line3"),
    ("postal_code", "address.postal_code"),
    ("construction_type", "construction_type"), ("type", "type"), ("budget_mm_usd", "budget_mm_usd"),
    ("construction_area_sqft", "construction_area_sqft"), ("land_area_acres", "land_area_acres"),
    ("max_building_height_ft", "max_building_height_ft"), ("project_id", "project_id")
  )

  def setProjectFields(projectId: String, nameValuePairs: Seq[(String, String)], request: HttpServletRequest,
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
        ProjectApi.validateNewName(name)
      case None =>
    }
    val projectOid = new ObjectId(projectId)
    val mongoDbSetters = nameValuePairs.map(p => (fullNames(p._1), p._2)).toMap
    val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> projectOid), Map("$set" -> mongoDbSetters))
    if (updateResult.getMatchedCount == 0)
      throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")
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