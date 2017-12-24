package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.{BWLogger, DateTimeUtils, HttpUtils}
import org.bson.types.ObjectId
import org.bson.Document

import scala.collection.JavaConverters._

class DocumentMetadataUpdate extends HttpServlet with HttpUtils with DateTimeUtils {

  private val fields = Set("category", "subcategory", "name", "description", "comments", "timestamp", "author_person_id")

  private def update(doc: Document): Unit = {
    val parameters = doc.asScala.toMap
    val nonIds = parameters.filter(t => fields.contains(t._1)).map {
      case ("timestamp", value: String) => ("timestamp", value.toLong)
      case ("author_person_id", value: String) => ("author_person_id", new ObjectId(value))
      case other => other
    }
    val oid = new ObjectId(parameters("document_master_id").asInstanceOf[String])
    val versionedParamNames = Set("timestamp", "author_person_id", "comments")
    val (versionedParams, nonVersionedParams) = nonIds.partition(p => versionedParamNames.contains(p._1))
    if (versionedParams.nonEmpty) {
      val docRecord: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> oid)).head
      val versions: Seq[DynDoc] = docRecord.versions[Many[Document]]
      val idx = versions.zipWithIndex.find(_._1.timestamp[Long] == versionedParams("timestamp")).head._2
      val updateResult = BWMongoDB3.document_master.updateOne(Map("_id" -> oid),
        Map("$set" -> versionedParams.filter(_._1 != "timestamp").map(t => (s"versions.$idx.${t._1}", t._2))))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB error: $updateResult")
    }
    if (nonVersionedParams.nonEmpty) {
      val updateResult = BWMongoDB3.document_master.updateOne(Map("_id" -> oid), Map("$set" -> nonVersionedParams))
      if (updateResult.getMatchedCount == 0)
        throw new IllegalArgumentException(s"MongoDB error: $updateResult")
    }
  }

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    try {
      val jsonArray = getStreamData(request)
      val arrayElements: Array[String] = jsonArray.substring(jsonArray.indexOf("{") + 1, jsonArray.lastIndexOf("}")).
          split("\\}\\s*,\\s*\\{")
      val documents = arrayElements.map(json => Document.parse(s"{$json}"))
      documents.foreach(update)
      BWLogger.log(getClass.getName, "doPost", s"EXIT-OK (${documents.length} items)", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        //t.printStackTrace()
        throw t
    }
  }
}
