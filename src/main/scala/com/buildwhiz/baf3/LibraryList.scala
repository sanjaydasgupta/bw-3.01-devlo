package com.buildwhiz.baf3

import com.buildwhiz.baf2.ActivityApi.dateTimeStringAmerican
import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.{BWMongoDBLib, DynDoc}
import com.buildwhiz.infra.BWMongoDBLib._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import org.bson.Document
import org.bson.types.ObjectId

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class LibraryList extends HttpServlet with HttpUtils {

  private def phaseList(requesting_organization_id: ObjectId, request: HttpServletRequest): Seq[Document] = {
    val user: DynDoc = getUser(request)
    val phases: Seq[DynDoc] = BWMongoDBLib.phases.find()
    def stripParenthesis(s: String) = s.substring(1).reverse.substring(1).reverse
    val phaseList: Seq[DynDoc] = phases.map(phase => {
      val libInfo: DynDoc = phase.library_info[Document]
      val timestamp = libInfo.timestamp[Long]
      val dateTime = dateTimeStringAmerican(timestamp, Some(user.tz[String]))
      Map("name" -> phase.name[String], "user_name" -> libInfo.user[String].split(" ").init.mkString(" "),
        "user_person_id" -> stripParenthesis(libInfo.user[String].split(" ").last), "timestamp" -> dateTime,
        "original_partner_name" -> libInfo.original_partner[String].split(" ").init.mkString(" "),
        "original_partner_organization_id" -> stripParenthesis(libInfo.original_partner[String].split(" ").last),
        "instance_name" -> libInfo.instance_name[String], "private" -> libInfo.`private`[Boolean],
        "flags" -> LibraryContentsUtility.trueFlagsList(BWMongoDBLib, phase),
        "original_project_name" -> libInfo.original_project[String].split(" ").init.mkString(" "),
        "original_project_id" -> stripParenthesis(libInfo.original_project[String].split(" ").last),
        "library_phase_id" -> phase._id[ObjectId].toString, "description" -> libInfo.description[String],
        "user_organization_id" -> stripParenthesis(libInfo.user_organization[String].split(" ").last)
      )
    })
    phaseList.filterNot(ph => ph.`private`[Boolean] && ph.user_organization_id[String] !=
        requesting_organization_id.toString)map(_.asDoc)
  }

  private def listAll(detail: Boolean): Seq[Document] = {
    val getName: DynDoc => String = dd => s"${dd.name[String]} (${dd._id[ObjectId]})"
    val recordToStringMap: Map[String, DynDoc => String] = Seq(
      "deliverables" -> getName,
      "process_schedules" -> ((dd: DynDoc) => s"${dd.title[String]} (${dd._id[ObjectId]})"),
      "organizations" -> getName,
      "projects" -> getName,
      "phases" -> getName,
      "processes" -> getName,
      "tasks" -> getName,
      "teams" -> ((dd: DynDoc) => s"${dd.team_name[String]} (${dd._id[ObjectId]})"),
      "persons" -> ((dd: DynDoc) => PersonApi.fullName(dd)),
      "deliverables" -> getName,
    ).toMap
    BWMongoDBLib.collectionNames.map(collName => (collName, BWMongoDBLib(collName))).map(t => {
      val (collectionName, collection) = t
      val count = collection.countDocuments()
      if (detail && recordToStringMap.contains(collectionName)) {
        val rows: Seq[DynDoc] = collection.aggregate(Seq(new Document("$project", new Document("name", true).
            append("title", true).append("team_name", true).append("first_name", true).append("last_name", true))))
        val rowStrings: Many[String] = rows.map(dd => recordToStringMap(collectionName)(dd))
        Map("collection" -> collectionName, "count" -> count, "rows" -> rowStrings)
      } else {
        Map("collection" -> collectionName, "count" -> count)
      }
    })
  }

  private def deleteLibrary(request: HttpServletRequest): String = {
    val user = getUser(request)
    val isAdmin = PersonApi.isBuildWhizAdmin(Right(user))
    if (isAdmin) {
      val deleteCounts = BWMongoDBLib.collectionNames.map(collName => (collName, BWMongoDBLib(collName))).map(t => {
        val deleteResult = t._2.deleteMany(new Document())
        (t._1, deleteResult.getDeletedCount)
      }).toMap
      val deletesJson = new Document(deleteCounts).toJson
      deletesJson
    } else {
      """{"ok": 0, "message": "Not permitted"}"""
    }
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)
    try {
      response.setContentType("application/json")
      val user: DynDoc = getPersona(request)
      val userOrgOid = user.organization_id[ObjectId]
      val parameters = getParameterMap(request)
      val (all, detail) = parameters.get("all") match {
        case Some(value) => (true, value.toBoolean)
        case None => (false, false)
      }
      val delete = parameters.get("delete") match {
        case Some(value) => value.toBoolean
        case None => false
      }
      val retJson: String = if (all) {
        listAll(detail).map(_.toJson).mkString("[", ", ", "]")
      } else if (delete) {
        deleteLibrary(request)
      } else {
        phaseList(userOrgOid, request).map(_.toJson).mkString("[", ", ", "]")
      }
      response.getWriter.println(retJson)
      BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
    } catch {
      case t: Throwable =>
        reportFatalException(t, getClass.getName, request, response)
    }
  }

}