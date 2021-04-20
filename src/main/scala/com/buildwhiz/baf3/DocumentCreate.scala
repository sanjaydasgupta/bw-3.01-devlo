package com.buildwhiz.baf3

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils, MailUtils}
import org.bson.Document
import org.bson.types.ObjectId
import com.buildwhiz.baf2.{PersonApi, ProjectApi}

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class DocumentCreate extends HttpServlet with HttpUtils with MailUtils with DateTimeUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, "ENTRY", request)
    val parameters = getParameterMap(request)
    try {
      type CONVERTER = String => Any
      val identity: CONVERTER = _.trim
      val oid: CONVERTER = new ObjectId(_)
      val toArray: CONVERTER = _.split(",").map(_.trim).toSeq
      val nameValuePairs: Map[String, Any] = Array(("deliverable_id", oid), ("activity_id", oid),
          ("phase_id", oid), ("project_id", oid), ("description", identity), ("file_format", identity),
          ("team_id", oid), ("name", identity), ("tags", toArray), ("category", identity)).
          flatMap(paramName => parameters.get(paramName._1).map(id => (paramName._1, paramName._2(id)))).toMap

      val projectOid = nameValuePairs.get("project_id") match {
        case Some(pOid) => pOid.asInstanceOf[ObjectId]
        case None =>
          throw new IllegalArgumentException("project_id not provided")
      }

      val user: DynDoc = getUser(request)
      if (!ProjectApi.canManage(user._id[ObjectId], ProjectApi.projectById(projectOid)) &&
          !PersonApi.isBuildWhizAdmin(Right(user)))
        throw new IllegalArgumentException("Not permitted")

      BWMongoDB3.document_master.insertOne(nameValuePairs ++ Map("versions" -> Seq.empty[Document]))

      response.getWriter.print(successJson())
      response.setContentType("application/json")
      val message = s"""Created document '${nameValuePairs("name")}'"""
      BWLogger.audit(getClass.getName, request.getMethod, message, request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, request.getMethod, s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}

