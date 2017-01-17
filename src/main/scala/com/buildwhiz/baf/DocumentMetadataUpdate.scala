package com.buildwhiz.baf

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.{BWLogger, BWMongoDB3}
import com.buildwhiz.{DateTimeUtils, HttpUtils}
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

class DocumentMetadataUpdate extends HttpServlet with HttpUtils with DateTimeUtils {

  override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    BWLogger.log(getClass.getName, "doPost", "ENTRY", request)
    val parameters = getParameterMap(request)
    val nonIds = parameters.filter(_._1 != "document_master_id").map {
      case ("timestamp", value) => ("timestamp", value.toLong)
      case ("author_person_id", value) => ("author_person_id", new ObjectId(value))
      case other => other
    }.toMap
    val oid = new ObjectId(parameters("document_master_id"))
    val versionedParamNames = Set("timestamp", "author_person_id", "comments")
    val (versionedParams, nonVersionedParams) = nonIds.partition(p => versionedParamNames.contains(p._1))
    try {
      if (versionedParams.nonEmpty) {
        val docRecord: DynDoc = BWMongoDB3.document_master.find(Map("_id" -> oid)).asScala.head
        val versions: Seq[DynDoc] = docRecord.versions[DocumentList]
        val idx = versions.zipWithIndex.find(_._1.timestamp[Long] == versionedParams("timestamp")).head._2
        val updateResult = BWMongoDB3.document_master.updateOne(Map("_id" -> oid),
          Map("$set" -> versionedParams.filter(_._1 != "timestamp").map(t => (s"versions.$idx.${t._1}", t._2))))
        if (updateResult.getModifiedCount == 0)
          throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      }
      if (nonVersionedParams.nonEmpty) {
        val updateResult = BWMongoDB3.document_master.updateOne(Map("_id" -> oid), Map("$set" -> nonVersionedParams))
        if (updateResult.getModifiedCount == 0)
          throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      }
      BWLogger.log(getClass.getName, "doPost", "EXIT-OK", request)
    } catch {
      case t: Throwable =>
        BWLogger.log(getClass.getName, "doPost", s"ERROR: ${t.getClass.getSimpleName}(${t.getMessage})", request)
        t.printStackTrace()
        throw t
    }
  }
}
