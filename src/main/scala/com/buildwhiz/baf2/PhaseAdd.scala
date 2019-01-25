package com.buildwhiz.baf2

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.Document
import org.bson.types.ObjectId

class PhaseAdd extends HttpServlet with HttpUtils {
  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {

    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      val parentProjectOid = new ObjectId(parameters("project_id"))
      val parentProject: Option[DynDoc] = BWMongoDB3.projects.find(Map("_id" -> parentProjectOid)).headOption
      if (parentProject.isEmpty)
        throw new IllegalArgumentException(s"Unknown project-id: '$parentProjectOid'")

      val user: DynDoc = getUser(request)
      val userOid = user._id[ObjectId]
      val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> userOid)).head
      val isAdmin = freshUserRecord.roles[Many[String]].contains("BW-Admin")
      val isProjectAdministrator = parentProject.get.admin_person_id[ObjectId] == userOid
      if (!isAdmin && !isProjectAdministrator)
        throw new IllegalArgumentException("Not permitted")

      val phaseName = parameters("phase_name")
      val optionalAdminPersonOid: Option[ObjectId] = parameters.get("admin_person_id").map(new ObjectId(_))
      val trueAdminPersonOid = optionalAdminPersonOid match {
        case None => userOid
        case Some(oid) => oid
      }
      if (BWMongoDB3.persons.find(Map("_id" -> trueAdminPersonOid)).isEmpty)
        throw new IllegalArgumentException(s"Unknown person-id: '$optionalAdminPersonOid'")

      val newPhaseRecord: Document = Map("name" -> phaseName, "admin_person_id" -> trueAdminPersonOid,
          "process_ids" -> Seq.empty[ObjectId], "assigned_roles" -> Seq.empty[Document], "status" -> "defined",
          "timestamps" -> Map("created" -> System.currentTimeMillis))
      BWMongoDB3.phases.insertOne(newPhaseRecord)

      val updateResult = BWMongoDB3.projects.updateOne(Map("_id" -> parentProjectOid),
          Map("$addToSet" -> Map("phase_ids" -> newPhaseRecord.y._id[ObjectId])))
      if (updateResult.getModifiedCount == 0)
        throw new IllegalArgumentException(s"MongoDB update failed: $updateResult")

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