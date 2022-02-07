package com.buildwhiz.baf2

import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

import scala.jdk.CollectionConverters._

class PhaseAdd extends HttpServlet with HttpUtils {
  private def doPostTransaction(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val parentProjectOid = new ObjectId(parameters("project_id"))
      if (!ProjectApi.exists(parentProjectOid))
        throw new IllegalArgumentException(s"Unknown project-id: '$parentProjectOid'")

      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      val isProjectAdmin = ProjectApi.isAdmin(userOid, ProjectApi.projectById(parentProjectOid))
      if (!PersonApi.isBuildWhizAdmin(Right(user)) && !isProjectAdmin)
        throw new IllegalArgumentException("Not permitted")

      val phaseName = parameters("phase_name")
      PhaseApi.validateNewName(phaseName, parentProjectOid)
      val description = parameters.get("description") match {
        case Some(desc) => desc
        case None => s"No description provided for '$phaseName'"
      }

      val phaseManagerOids: Seq[ObjectId] = parameters.get("manager_ids") match {
        case None => Seq.empty[ObjectId]
        case Some(ids) => ids.split(",").map(_.trim).filter(_.nonEmpty).distinct.map(new ObjectId(_)).toSeq
      }

      val badManagerIds = phaseManagerOids.filterNot(PersonApi.exists)
      if (badManagerIds.nonEmpty)
        throw new IllegalArgumentException(s"""Bad project_manager_ids: ${badManagerIds.mkString(", ")}""")

      val managersInRoles = phaseManagerOids.map(oid =>
        new Document("role_name", "Project-Manager").append("person_id", oid)
      ).asJava

      val optionalAdminPersonOid: Option[ObjectId] = parameters.get("admin_person_id").map(new ObjectId(_))
      val trueAdminPersonOid = optionalAdminPersonOid match {
        case None => userOid
        case Some(oid) => oid
      }
      if (!PersonApi.exists(trueAdminPersonOid))
        throw new IllegalArgumentException(s"Unknown person-id: '$trueAdminPersonOid'")

      val newPhaseRecord: Document = Map("name" -> phaseName, "admin_person_id" -> trueAdminPersonOid,
          "process_ids" -> Seq.empty[ObjectId], "assigned_roles" -> managersInRoles, "status" -> "defined",
          "timestamps" -> Map("created" -> System.currentTimeMillis), "description" -> description)
      BWMongoDB3.phases.insertOne(newPhaseRecord)

      val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> parentProjectOid),
          Map("$addToSet" -> Map("phase_ids" -> newPhaseRecord.y._id[ObjectId])))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

      response.getWriter.print(bson2json(newPhaseRecord))
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

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWMongoDB3.withTransaction(doPostTransaction(request, response))
  }
}