package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.{ActivityApi, ProcessApi}
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.BWMongoDB3
import com.buildwhiz.utils.BWLogger
import javax.servlet.http.HttpServletRequest
import org.bson.types.ObjectId
import org.bson.Document

object MigrateProcessData {

  def activityTranslationMap(request: HttpServletRequest, fromProcessOid: ObjectId, toProcessOid: ObjectId):
      Map[ObjectId, ObjectId] = {

    val fromActivities = ProcessApi.allActivities(fromProcessOid).sortBy(_.name[String])
    val toActivities = ProcessApi.allActivities(toProcessOid).sortBy(_.name[String])

    val fromActivityNames = fromActivities.map(_.name[String])
    val toActivityNames = toActivities.map(_.name[String])

    val namesDontMatch = fromActivityNames.zip(toActivityNames).exists(pair => pair._1 != pair._2)
    if (namesDontMatch || fromActivityNames.length != toActivityNames.length) {
      BWLogger.log(getClass.getName, "activityTranslationMap()",
          "Incompatible processes ... Exiting", request)
      throw new IllegalArgumentException("Processes not compatible for migration")
    }

    val fromActivityOids = fromActivities.map(_._id[ObjectId])
    val toActivityOids = toActivities.map(_._id[ObjectId])

    fromActivityOids.zip(toActivityOids).toMap
  }

  def copyActivityData(request: HttpServletRequest, fromProcessOid: ObjectId, toProcessOid: ObjectId): Unit = {

    BWLogger.log(getClass.getName, "copyActivityChangeLogs",
        "Calling: activityTranslations()", request)
    val activityTranslations = activityTranslationMap(request, fromProcessOid, toProcessOid)

    for ((fromActivityOid, toActivityOid) <- activityTranslations) {

      val fromActivity = ActivityApi.activityById(fromActivityOid)
      if (fromActivity.has("change_log")) {
        val fromChangeLog = fromActivity.change_log[Many[Document]]
        val updateResult = BWMongoDB3.activities.updateOne(Map("_id" -> toActivityOid),
          Map($push -> Map("change_log" -> Map($each -> fromChangeLog))))
        if (updateResult.getMatchedCount == 0)
          throw new IllegalArgumentException(s"MongoDB error: $updateResult")
      }

      BWMongoDB3.document_master.updateMany(Map("activity_id" -> fromActivityOid),
          Map($set -> Map("activity_ids" -> Seq(fromActivityOid, toActivityOid))))

    }

    BWLogger.log(getClass.getName, "copyActivityChangeLogs",
        s"Copied change-logs of ${activityTranslations.size} activities", request)
  }

  def migrate(request: HttpServletRequest, fromProcessOid: ObjectId, toProcessOid: ObjectId): Unit = {
    if (!ProcessApi.exists(fromProcessOid))
      throw new IllegalArgumentException(s"Bad from-process-id $fromProcessOid")
    if (!ProcessApi.exists(toProcessOid))
      throw new IllegalArgumentException(s"Bad to-process-id $toProcessOid")

    BWLogger.log(getClass.getName, "main()", "Calling: copyAssignments()", request)
    copyActivityData(request, fromProcessOid, toProcessOid)
  }

  def main(request: HttpServletRequest, args: Array[String] = Array.empty[String]): Unit = {
    BWLogger.log(getClass.getName, "main()", "ENTRY", request)
    if (args.length == 2) {
      val fromProcessOid = new ObjectId(args(0))
      val toProcessOid = new ObjectId(args(1))
      BWLogger.log(getClass.getName, "main()",
          s"Calling: migrate($fromProcessOid, $toProcessOid)", request)
      migrate(request, fromProcessOid, toProcessOid)
      BWLogger.log(getClass.getName, "main()", "EXIT-OK", request)
    } else {
      BWLogger.log(getClass.getName, "main()",
          "EXIT Usage: MigrateProcess from-process-id, to-process-id", request)
    }
  }

}
