package com.buildwhiz.baf

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, HttpUtils}

import scala.sys.process._
import scala.languageFeature.{implicitConversions, postfixOps}
import scala.collection.JavaConverters._
import org.bson.Document

class MongoDBView extends HttpServlet with HttpUtils {

  private def archive(request: HttpServletRequest): String = {
    val archivedFiles = new java.io.File(".").listFiles().filter(_.getName.matches("mongodb-.{20,30}\\.archive"))
    val filesToDelete = archivedFiles.sortBy(f => -f.lastModified).drop(3)
    filesToDelete.foreach(_.delete())
    val ftd = filesToDelete.map(n => s""" "${n.getName}" """.trim).mkString("[", ", ", "]")
    val dateTimeString = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now())
    val fileName = s"mongodb-$dateTimeString.archive"
    val status: Int = f"mongodump --db=BuildWhiz --archive=$fileName".!
    s"""{"status": $status, "new_archive": "$fileName", "purged_archives": $ftd}"""
  }

  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val parameters = getParameterMap(request)
    BWLogger.log(getClass.getName, "doGet", "ENTRY", request)
    val writer = response.getWriter
    try {
      parameters.get("collection_name") match {
        case None =>
          val names: Seq[String] = BWMongoDB3.collectionNames
          val counts: Seq[Long] = names.map(BWMongoDB3(_).count())
          val nameAndCounts = names.zip(counts).sortWith(_._1 < _._1)
          val jsonStrings = nameAndCounts.map(nc => s"""{"name": "${nc._1}", "count": ${nc._2}}""")
          writer.print(jsonStrings.mkString("[", ", ", "]"))
        case Some("*") =>
          writer.println(archive(request))
        case Some(collectionName) =>
          val docs: Seq[Document] = BWMongoDB3(collectionName).find().limit(100).asScala.toSeq
          val jsonStrings: Seq[String] = docs.map(_.toJson)
          writer.print(jsonStrings.mkString("[", ", ", "]"))
      }
      response.setContentType("application/json")
      response.setStatus(HttpServletResponse.SC_OK)
      BWLogger.log(getClass.getName, "doGet", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doGet", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
