package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.amazonaws.services.s3.model.S3ObjectSummary
import com.buildwhiz.{DateTimeUtils, HttpUtils}
import com.buildwhiz.infra.{AmazonS3, BWLogger, BWMongoDB3}
import BWMongoDB3._
import org.bson.types.ObjectId

import scala.collection.JavaConversions._

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
    val writer = response.getWriter
    try {
      val parameters = getParameterMap(request)
      val person: DynDoc = BWMongoDB3.persons.find(Map("_id" -> new ObjectId(parameters("person_id")))).head
      val objectSummaries: Seq[S3ObjectSummary] = AmazonS3.listObjects.getObjectSummaries
      val namesAndSizes: Seq[(String, Long)] = objectSummaries.map(obj => (obj.getKey, obj.getSize))
      val projectDocumentTimestampSize: Seq[(String, String, String, Long)] =
        namesAndSizes.map(t => {val f = t._1.split("-"); (f(0), f(1), f(2), t._2)})
      val byProject: Seq[String] = projectDocumentTimestampSize.groupBy(_._1).
        map(p => (p._1, p._2.map(q => (q._2, time(q._3, person.tz[String]), q._4)))).
        map(p => (p._1, p._2.map(t => s"""{"doc_id": "${t._1}", "ts": "${t._2}", "size": ${t._3}}"""))).
        map(p => (p._1, p._2.mkString("[", ", ", "]"))).
        map(p => s"""{"project": "${projectId2IdAndName(p._1)}", "docs": ${p._2}}""").toSeq
      writer.print(byProject.mkString("[", ", ", "]"))
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet()", s"EXIT-OK (${namesAndSizes.length} objects)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet()", s"ERROR: ${t.getClass.getName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }

}