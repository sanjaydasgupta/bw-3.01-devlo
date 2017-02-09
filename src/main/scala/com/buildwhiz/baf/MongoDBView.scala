package com.buildwhiz.baf

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.infra.BWMongoDB3.{DynDoc, ManyThings}
import com.buildwhiz.utils.{BWLogger, HttpUtils}
import com.mongodb.client.FindIterable

import scala.sys.process._
import scala.languageFeature.{implicitConversions, postfixOps}
import scala.collection.JavaConverters._
import org.bson.Document

import scala.collection.mutable

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

  private def collectionSchema(request: HttpServletRequest, response: HttpServletResponse, collectionName: String) = {
    def generateSchema(baseName: String, obj: Any, acc: mutable.Map[String, (Int, Set[String])]): Unit = obj match {
      case dd: DynDoc => generateSchema(baseName, dd.asDoc, acc)
      case fi: FindIterable[Document] @unchecked => fi.asScala.foreach(d => generateSchema(baseName, d, acc))
      case list: ManyThings => list.asScala.foreach(d => generateSchema(baseName + "[]", d, acc))
      case document: Document => for ((k, v) <- document.asScala) {
        val typ = v.getClass.getSimpleName
        val newKey = if (baseName.isEmpty) k else s"$baseName.$k"
        if (acc.contains(newKey)) {
          val schema = acc(newKey)
          acc(newKey) = (schema._1 + 1, schema._2 + typ)
        } else {
          acc(newKey) = (1, Set(typ))
        }
        if (v.isInstanceOf[Document] || v.isInstanceOf[ManyThings])
          generateSchema(newKey, v, acc)
      }
      case x =>
        val typ = x.getClass.getSimpleName
        if (acc.contains(baseName)) {
          val schema = acc(baseName)
          acc(baseName) = (schema._1 + 1, schema._2 + typ)
        } else {
          acc(baseName) = (1, Set(typ))
        }
    }
    val writer = response.getWriter
    val schema = mutable.Map.empty[String, (Int, Set[String])]
    generateSchema("", BWMongoDB3(collectionName).find(), schema)
    val rows = schema.toSeq.sortWith(_._1 < _._1).map(s => s"""["${s._1}", "${s._2._2.mkString(", ")}", ${s._2._1}]""")
    val array = rows.mkString("[", ", ", "]")
    writer.println(array)
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
          if (collectionName.endsWith("*")) {
            collectionSchema(request, response, collectionName.substring(0, collectionName.length - 1))
          } else {
            val docs: Seq[Document] = BWMongoDB3(collectionName).find().limit(100).asScala.toSeq
            val jsonStrings: Seq[String] = docs.map(_.toJson)
            writer.print(jsonStrings.mkString("[", ", ", "]"))
          }
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
