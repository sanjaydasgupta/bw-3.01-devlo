package com.buildwhiz.baf3

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._
import com.buildwhiz.baf2.{OrganizationApi, PersonApi, PhaseApi, ProjectApi}

import math.random

class ProjectList extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val scope = parameters.getOrElse("scope", "all")
      parameters.get("reset_persona").map(_.toBoolean) match {
        case Some(true) => resetPersona(request)
        case _ => // do nothing (default project list call)
      }
      val user: DynDoc = getPersona(request)
      val userOid = user._id[ObjectId]
      val optCustomerOid = parameters.get("customer_id").map(new ObjectId(_))
      val projects = ProjectList.getList(userOid, scope = scope, optCustomerOid = optCustomerOid, request = request)
      val projectsInfo: Many[Document] = projects.map(project => projectInfo(project, user)).asJava
      val canCreateNewProject = PersonApi.isBuildWhizAdmin(Left(userOid))
      val result = new Document("can_create_new_project", canCreateNewProject).append("projects", projectsInfo).
          append("menu_items", displayedMenuItems(PersonApi.isBuildWhizAdmin(Right(user)), starting = true))
      response.getWriter.print(result.toJson)
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${projects.length})", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  private def rint(): String = (random() * 15).toInt.toString

  private def projectInfo(project: DynDoc, user: DynDoc): Document = {
    val userIsAdmin = PersonApi.isBuildWhizAdmin(Right(user))
    val phases: Seq[DynDoc] = ProjectApi.allPhases(project).map(phase => {
      Map("name" -> phase.name[String], "_id" -> phase._id[ObjectId].toString,
      "display_status" -> PhaseApi.displayStatus3(phase), "alert_count" -> rint(), "rfi_count" -> rint(),
      "issue_count" -> rint(), "discussion_count" -> rint(), "budget" -> "1.5 MM", "expenditure" -> "350,500")
    })
    val address: DynDoc = project.address[Document]
    val customerName = project.get[ObjectId]("customer_organization_id") match {
      case None => "Not available"
      case Some(custOrgId) => OrganizationApi.organizationById(custOrgId).name[String]
    }
    val name = project.name[String]
    val summary = project.get[String]("summary") match {
      case None => s"Summary for '$name'"
      case Some(theSummary) => theSummary
    }
    val distinctPhaseStatusValues: Seq[String] = phases.map(_.display_status[String]).distinct
    val displayStatus3 = distinctPhaseStatusValues.length match {
      case 0 => "Unknown"
      case 1 => distinctPhaseStatusValues.head
      case _ => "Active"
    }
    Map("name" -> name, "_id" -> project._id[ObjectId].toString, "summary" -> summary,
        "display_status" -> displayStatus3,
        "address_line1" -> address.line1[String],
        "address_line2" -> address.line2[String], "address_line3" -> address.line3[String],
        "postal_code" -> address.postal_code[String], "country" -> address.country[Document],
        "state" -> address.state[Document], "gps_location" -> address.gps_location[Document],
        "phases" -> phases.map(_.asDoc).asJava, "description" -> project.description[String],
        "image_url" -> ProjectApi.imageUrl(Right(project)), "customer" -> customerName
    )
  }
}

object ProjectList extends HttpUtils {
  def getList(userOid: ObjectId, scope: String = "all", optCustomerOid: Option[ObjectId] = None,
      request: HttpServletRequest, doLog: Boolean = false): Seq[DynDoc] = {
    if (doLog)
      BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    // scope: must be one of past/current/future/all
    val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> userOid)).head
    val scopeQuery = (scope, optCustomerOid) match {
      case ("past", None) => Map("status" -> "ended")
      case ("past", Some(customerOid)) => Map("status" -> "ended", "customer_organization_id" -> customerOid)
      case ("current", None) => Map("status" -> "running")
      case ("current", Some(customerOid)) => Map("status" -> "running", "customer_organization_id" -> customerOid)
      case ("future", None) => Map("status" -> "defined")
      case ("future", Some(customerOid)) => Map("status" -> "defined", "customer_organization_id" -> customerOid)
      case ("all", None) => Map.empty[String, Any]
      case ("all", Some(customerOid)) => Map("customer_organization_id" -> customerOid)
      case _ => Map.empty[String, Any]
    }
    val isAdmin = PersonApi.isBuildWhizAdmin(Right(freshUserRecord))
    val projects: Seq[DynDoc] = if (isAdmin) {
      BWMongoDB3.projects.find(scopeQuery)
    } else {
      ProjectApi.projectsByQuery30(scopeQuery, Some(userOid))
    }
    if (doLog)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK (${projects.length})", request)
    projects
  }
}