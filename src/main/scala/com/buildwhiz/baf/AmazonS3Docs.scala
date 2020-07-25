package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.{DynDoc, FileMetadata}
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{AmazonS3, BWMongoDB3}
import BWMongoDB3._
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.types.ObjectId

class AmazonS3Docs extends HttpServlet with HttpUtils with DateTimeUtils {

  private def time(hexMs: String, tz: String): String =
    dateTimeString(java.lang.Long.parseLong(hexMs, 16), Some(tz))

  private def projectId2IdAndName(projectId: String): String = {
    val name = BWMongoDB3.projects.find(Map("_id" -> new ObjectId(projectId))).headOption match {
      case None => "???"
      case Some(projectDocument) => val project: DynDoc = projectDocument
        project.name[String]
    }
    s"$projectId ($name)"
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doGet()", s"ENTRY", request)
    try {
      val parameters = getParameterMap(request)
      val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> new ObjectId(parameters("person_id")))).head
      val objectSummaries: Seq[FileMetadata] = AmazonS3.listObjects
      val namesAndSizes: Seq[(String, Long)] = objectSummaries.map(obj => (obj.key, obj.size))
      val projectDocumentTimestampSize: Seq[(String, String, String, Long)] =
        namesAndSizes.map(t => {val f = t._1.split("-"); (f(0), f(1), f(2), t._2)})
      val byProject: Seq[String] = projectDocumentTimestampSize.groupBy(_._1).
        map(p => (p._1, p._2.map(q => (q._2, time(q._3, person.tz[String]), q._4)))).
        map(p => (p._1, p._2.map(t => s"""{"doc_id": "${t._1}", "ts": "${t._2}", "size": ${t._3}}"""))).
        map(p => (p._1, p._2.mkString("[", ", ", "]"))).
        map(p => s"""{"project": "${projectId2IdAndName(p._1)}", "docs": ${p._2}}""").toSeq
      response.getWriter.print(byProject.mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK (${namesAndSizes.length} objects)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

  override def doDelete(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doDelete()", s"ENTRY", request)
    try {
      val parameters = getParameterMap(request)
      val projectId = parameters("project_id")
      val objectSummaries: Seq[FileMetadata] = AmazonS3.listObjects(projectId)
      for (summary <- objectSummaries) {
        AmazonS3.deleteObject(summary.key)
      }
      response.getWriter.println(s"""{"count": ${objectSummaries.length}}""")
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doDelete()", s"EXIT-OK (${objectSummaries.length} objects)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doDelete()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }

}