package com.buildwhiz.baf

import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.bson.types.ObjectId
import org.bson.Document

class GetProjectList extends HttpServlet with HttpUtils {

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, request.getMethod, s"ENTRY", request)

    val user: DynDoc = getUser(request)
    val freshUserRecord: DynDoc = BWMongoDB3.persons.find(Map("_id" -> user._id[ObjectId])).head
    val projectOids: Seq[ObjectId] = freshUserRecord.project_ids[Many[ObjectId]]
    val projects: Seq[DynDoc] = BWMongoDB3.projects.find(Map("_id" -> Map("$in" -> projectOids)))

    val reply: Seq[Document] = projects.map(project =>
      Map("_id" -> project._id[ObjectId].toString, "status" -> project.status[String], "name" -> project.name[String])
    ).map(m => DynDoc.mapToDocument(m))

    response.getWriter.println(reply.map(_.toJson).mkString("[", ", ", "]"))
    response.setContentType("application/json")
    response.setStatus(HttpServletResponse.SC_OK)

    BWLogger.log(getClass.getName, request.getMethod, s"EXIT-OK", request)
  }

}